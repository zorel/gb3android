package org.zorel.gb3android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.BoardClient
import org.zorel.gb3android.data.BoardCodec
import org.zorel.gb3android.data.ConnectionMode
import org.zorel.gb3android.data.EmojiItem
import org.zorel.gb3android.data.Gb3Api
import org.zorel.gb3android.data.LinuxfrSession
import org.zorel.gb3android.data.PollEvent
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Preferences
import org.zorel.gb3android.data.PredefinedBoards
import org.zorel.gb3android.data.Screen
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.data.Totoz
import org.zorel.gb3android.data.TribuneBadge
import org.zorel.gb3android.data.csvSet
import org.zorel.gb3android.data.randomNickname
import org.zorel.gb3android.domain.Conversation
import org.zorel.gb3android.domain.MessageParser
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.domain.ParsedMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

private const val MAX_MESSAGE_LENGTH = 65_536
private const val BOARD_POLL_INTERVAL_MS = 30_000L
private val PLOP_WORDS = listOf("pika", "plop", "prout", "grouik", "meuh")

private val ALT_TEMPLATES = mapOf(
    "blam" to "_o/* <b>BLAM</b>! ",
    "moment" to "====> <b>Moment %s</b> <====",
    "phi" to "φ",
    "bold" to "<b>%s</b>",
    "italic" to "<i>%s</i>",
    "underline" to "<u>%s</u>",
    "strikeThrough" to "<s>%s</s>",
    "teletext" to "<tt>%s</tt>",
    "code" to "<code>%s</code>",
    "spoiler" to "<spoiler>%s</spoiler>",
    "paf" to "_o/* <b>paf!</b> ",
    "acappella" to "♪ <i>%s</i> ♪",
)

class Gb3ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(application)
    private val api = Gb3Api()
    private val boardClient = BoardClient()

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val allPosts = LinkedHashMap<String, Post>()
    private val parsedCache = LinkedHashMap<String, ParsedMessage>()
    private val plopOverrides = HashMap<String, String>()
    private var selectedKey: String? = null
    private var analysis: Conversation.Analysis? = null
    private var serverBlocked: Set<String> = emptySet()
    private var foreground = false
    private var bootstrapJob: Job? = null
    private var pollJob: Job? = null
    private var boardPollJob: Job? = null
    private var recomputeJob: Job? = null
    private var pollSignature: String? = null
    private var boardPollSignature: String? = null
    private val boardStatus = HashMap<String, Boolean>()
    private val reportedBoardFailures = HashSet<String>()

    /** Message recovered after an interrupted LinuxFR OAuth flow. */
    private var pendingDraftOnStart: String? = prefs.pendingLinuxfrMessage

    init {
        val nickname = prefs.nickname.ifBlank {
            randomNickname().also { prefs.nickname = it }
        }
        val pending = pendingDraftOnStart
        val draftText = pending ?: prefs.draft
        if (pending != null) {
            prefs.pendingLinuxfrMessage = null
            prefs.draft = draftText
        }
        val mode = prefs.connectionMode
        val boards = PredefinedBoards.merge(BoardCodec.decode(prefs.customBoardsJson))
        val boardNames = boards.mapTo(HashSet()) { it.name }
        val stored = prefs.selectedTribunesCsv.csvSet()
        val directSelected = if (mode == ConnectionMode.DirectBoards) {
            stored.filter { it in boardNames }.sorted()
        } else {
            emptyList()
        }
        val setupRequired = when (mode) {
            ConnectionMode.Server -> prefs.serverUrl.isBlank()
            ConnectionMode.DirectBoards -> directSelected.isEmpty()
        }
        _state.value = UiState(
            serverUrl = prefs.serverUrl,
            connectionMode = mode,
            boards = boards,
            nickname = nickname,
            availableTribunes = if (mode == ConnectionMode.DirectBoards) {
                boardNames.sorted()
            } else {
                emptyList()
            },
            tribunes = directSelected,
            activeTribune = directSelected.firstOrNull(),
            localBlocked = prefs.localBlockedIdentitiesCsv.csvSet(),
            displayMode = prefs.blockedDisplayMode,
            plopList = prefs.plopListCsv.csvSet(),
            draft = TextDraft(draftText, draftText.length, draftText.length),
            linuxfr = prefs.linuxfrSession,
            lastPosted = prefs.lastPostedMessage.ifBlank { null },
            setupRequired = setupRequired,
            initializing = !setupRequired,
        )
        if (!setupRequired) bootstrap()
    }

    fun onForeground() {
        foreground = true
        val current = _state.value
        if (current.setupRequired) return
        if (current.directBoards) {
            if (bootstrapJob?.isActive == true || boardPollJob?.isActive == true) return
            if (current.tribunes.isEmpty()) {
                bootstrap()
            } else {
                startBoardPolling(current.tribunes)
            }
            return
        }
        if (current.serverUrl.isBlank()) return
        if (bootstrapJob?.isActive == true || pollJob?.isActive == true) return
        if (current.tribunes.isEmpty()) {
            bootstrap()
        } else {
            startPolling(current.serverUrl, current.tribunes)
        }
    }

    fun onBackground() {
        foreground = false
        stopPolling()
        stopBoardPolling()
    }

    fun reconnect() {
        val current = _state.value
        if (current.directBoards) {
            if (current.tribunes.isEmpty()) {
                emit("no board selected", isError = true)
                return
            }
            if (!foreground) {
                emit("reconnect the app to resume the board refresh", isError = true)
                return
            }
            emit("refreshing boards…")
            startBoardPolling(current.tribunes, force = true)
            return
        }
        if (current.serverUrl.isBlank()) return
        if (!foreground) {
            emit("reconnect the app to resume the live stream", isError = true)
            return
        }
        if (bootstrapJob?.isActive == true) return
        if (current.tribunes.isEmpty()) {
            bootstrap()
        } else {
            emit("reconnecting…")
            startPolling(current.serverUrl, current.tribunes, force = true)
        }
    }

    private fun bootstrap(clearPosts: Boolean = false) {
        bootstrapJob?.cancel()
        stopPolling()
        stopBoardPolling()
        if (clearPosts) {
            allPosts.clear()
            parsedCache.clear()
            plopOverrides.clear()
            selectedKey = null
        }
        when (_state.value.connectionMode) {
            ConnectionMode.DirectBoards -> bootstrapDirect()
            ConnectionMode.Server -> bootstrapServer(clearPosts)
        }
    }

    /**
     * Direct-board mode has no server to discover tribunes from: the available
     * list comes from the built-in definitions and the user's custom boards.
     */
    private fun bootstrapDirect() {
        val available = _state.value.boards.map { it.name }.sorted()
        val stored = prefs.selectedTribunesCsv.csvSet()
        val selected = available.filter { it in stored }
        if (selected.isEmpty()) {
            _state.update {
                it.copy(
                    initializing = false,
                    connecting = false,
                    connected = false,
                    availableTribunes = available,
                    tribunes = emptyList(),
                    activeTribune = null,
                    serverBlocked = emptySet(),
                    displayPosts = emptyList(),
                    badges = emptyMap(),
                    selectedKey = null,
                    setupRequired = true,
                )
            }
            recomputeNow()
            emit("select at least one board", isError = true)
            return
        }

        prefs.selectedTribunesCsv = selected.joinToString(",")
        val currentActive = _state.value.activeTribune
        val active = when {
            pendingDraftOnStart != null && "dlfp" in selected -> "dlfp"
            else -> currentActive?.takeIf { it in selected } ?: selected.first()
        }
        pendingDraftOnStart = null
        selectedKey = null
        serverBlocked = emptySet()
        _state.update {
            it.copy(
                initializing = false,
                connecting = foreground,
                connected = false,
                availableTribunes = available,
                tribunes = selected,
                activeTribune = active,
                serverBlocked = emptySet(),
                selectedKey = null,
                setupRequired = false,
            )
        }
        recomputeNow()
        if (foreground) {
            startBoardPolling(selected)
        } else {
            _state.update { it.copy(connecting = false) }
        }
    }

    private fun bootstrapServer(clearPosts: Boolean) {
        val normalized = Gb3Api.normalizeServerUrl(_state.value.serverUrl)
        if (normalized == null) {
            _state.update {
                it.copy(
                    initializing = false,
                    connecting = false,
                    connected = false,
                    setupRequired = true,
                )
            }
            if (_state.value.serverUrl.isNotBlank()) emit("invalid server URL", isError = true)
            return
        }
        prefs.serverUrl = normalized
        _state.update { it.copy(serverUrl = normalized, initializing = true, connecting = true) }
        bootstrapJob = viewModelScope.launch {
            val known = prefs.knownTribunesCsv.csvSet().toList().sorted()
            val availableResult = try {
                Result.success(api.tribunes(normalized))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            val available = availableResult.getOrElse {
                emit("cannot list tribunes: ${it.message ?: "network error"}", isError = true)
                known
            }
            if (availableResult.isSuccess) {
                prefs.knownTribunesCsv = available.joinToString(",")
            }

            val configResult = try {
                Result.success(api.blockedIdentities(normalized))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (configResult.isSuccess) {
                serverBlocked = configResult.getOrThrow().toSet()
            } else if (clearPosts) {
                serverBlocked = emptySet()
            }

            val stored = prefs.selectedTribunesCsv.csvSet()
            val selected = when {
                // A successful, empty list means the server really has no
                // tribune. Only fall back to cached names when the list request
                // failed.
                availableResult.isSuccess && available.isEmpty() -> emptyList()
                available.isEmpty() -> stored.toList().sorted()
                stored.isEmpty() -> available
                else -> {
                    val filtered = available.filter { it in stored }
                    if (filtered.isEmpty()) available else filtered
                }
            }

            if (selected.isEmpty()) {
                _state.update {
                    it.copy(
                        initializing = false,
                        connecting = false,
                        connected = false,
                        availableTribunes = available,
                        tribunes = emptyList(),
                        activeTribune = null,
                        serverBlocked = serverBlocked,
                        displayPosts = emptyList(),
                        badges = emptyMap(),
                        selectedKey = null,
                    )
                }
                recomputeNow()
                emit("no tribune available on this server", isError = true)
                return@launch
            }

            prefs.selectedTribunesCsv = selected.joinToString(",")
            val currentActive = _state.value.activeTribune
            val active = when {
                pendingDraftOnStart != null && "dlfp" in selected -> "dlfp"
                else -> currentActive?.takeIf { it in selected } ?: selected.first()
            }
            pendingDraftOnStart = null
            selectedKey = null
            _state.update {
                it.copy(
                    initializing = false,
                    availableTribunes = available,
                    tribunes = selected,
                    activeTribune = active,
                    serverBlocked = serverBlocked,
                    selectedKey = null,
                    setupRequired = false,
                )
            }
            recomputeNow()
            if (foreground) {
                startPolling(normalized, selected)
            } else {
                _state.update { it.copy(connecting = false) }
            }
        }
    }

    private fun startPolling(baseUrl: String, tribunes: List<String>, force: Boolean = false) {
        val signature = "$baseUrl|${tribunes.joinToString(",")}"
        if (!force && signature == pollSignature && pollJob?.isActive == true) return
        pollJob?.cancel()
        pollSignature = signature
        if (tribunes.isEmpty()) {
            _state.update { it.copy(connecting = false, connected = false) }
            return
        }
        _state.update { it.copy(connecting = true, connected = false) }
        pollJob = viewModelScope.launch {
            try {
                api.poll(baseUrl, tribunes).collect { event ->
                    when (event) {
                        PollEvent.Connecting -> _state.update {
                            it.copy(connecting = true, connected = false)
                        }

                        PollEvent.Connected -> _state.update {
                            it.copy(connecting = false, connected = true)
                        }

                        is PollEvent.NewPost -> ingest(event.post)

                        is PollEvent.Failed -> _state.update {
                            it.copy(connecting = true, connected = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(connecting = false, connected = false) }
                emit(error.message ?: "poll stream stopped", isError = true)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        pollSignature = null
        _state.update { it.copy(connected = false, connecting = false) }
    }

    /**
     * Poll every selected board directly, at gb2c's usual 30 second interval.
     * Each board has its own loop so a slow or broken board cannot delay the
     * others.
     */
    private fun startBoardPolling(tribunes: List<String>, force: Boolean = false) {
        val boards = _state.value.boards
            .filter { it.name in tribunes }
            .sortedBy { it.name }
        val signature = boards.joinToString(",") { "${it.name}|${it.backendUrl}" }
        if (!force && signature == boardPollSignature && boardPollJob?.isActive == true) return
        boardPollJob?.cancel()
        boardPollSignature = signature
        boardStatus.clear()
        reportedBoardFailures.clear()
        if (boards.isEmpty()) {
            _state.update { it.copy(connecting = false, connected = false) }
            return
        }
        boards.forEach { boardStatus[it.name] = false }
        _state.update { it.copy(connecting = true, connected = false) }
        boardPollJob = viewModelScope.launch {
            coroutineScope {
                for (board in boards) {
                    launch {
                        while (isActive) {
                            val started = System.currentTimeMillis()
                            refreshBoard(board)
                            val elapsed = System.currentTimeMillis() - started
                            delay((BOARD_POLL_INTERVAL_MS - elapsed).coerceAtLeast(1_000L))
                        }
                    }
                }
            }
        }
    }

    /** Fetch one board once and update the connection indicator. */
    private suspend fun refreshBoard(board: Board) {
        try {
            val posts = boardClient.fetch(board)
            posts.forEach(::ingest)
            boardStatus[board.name] = true
            reportedBoardFailures.remove(board.name)
            updateBoardConnection()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            boardStatus[board.name] = false
            updateBoardConnection()
            if (reportedBoardFailures.add(board.name)) {
                emit(
                    "cannot read ${board.name}: ${error.message ?: "network error"}",
                    isError = true,
                )
            }
        }
    }

    private fun updateBoardConnection() {
        val anyConnected = boardStatus.values.any { it }
        _state.update { it.copy(connected = anyConnected, connecting = false) }
    }

    private fun stopBoardPolling() {
        boardPollJob?.cancel()
        boardPollJob = null
        boardPollSignature = null
        boardStatus.clear()
        reportedBoardFailures.clear()
        _state.update { it.copy(connected = false, connecting = false) }
    }

    private fun ingest(post: Post) {
        if (post.tribune !in _state.value.tribunes) return
        val existing = allPosts[post.key]
        if (existing == post) return
        allPosts[post.key] = post
        if (post.info in _state.value.plopList && !plopOverrides.containsKey(post.key)) {
            plopOverrides[post.key] = PLOP_WORDS.random()
        }
        scheduleRecompute()
    }

    private fun scheduleRecompute() {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            delay(60)
            recomputeNow()
        }
    }

    private fun recomputeNow() {
        val current = _state.value
        val posts = allPosts.values.toList()
        val parsedByKey = posts.associate { post -> post.key to parsedFor(post.message) }
        val identities = buildSet {
            if (current.nickname.isNotBlank()) add(current.nickname)
            current.linuxfr?.login?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val computed = Conversation.analyze(
            posts = posts,
            parsedByKey = parsedByKey,
            blockedIdentities = current.localBlocked + serverBlocked,
            myIdentities = identities,
        )
        analysis = computed

        for (post in posts) {
            if (post.info in current.plopList && !plopOverrides.containsKey(post.key)) {
                plopOverrides[post.key] = PLOP_WORDS.random()
            }
        }
        plopOverrides.keys.removeAll { key -> allPosts[key]?.info !in current.plopList }

        val active = current.activeTribune
        val display = if (active == null) {
            emptyList()
        } else {
            posts.asSequence()
                .filter { it.tribune == active }
                .sortedBy { it.id }
                .mapNotNull { post ->
                    val conversation = computed.posts[post.key] ?: return@mapNotNull null
                    if (conversation.hidden && current.displayMode == BlockedDisplayMode.Hide) {
                        return@mapNotNull null
                    }
                    val displayMessage = when {
                        conversation.hidden -> "[message hidden]"
                        plopOverrides[post.key] != null -> plopOverrides.getValue(post.key)
                        else -> post.message
                    }
                    val displayParsed = if (displayMessage == post.message) {
                        parsedByKey.getValue(post.key)
                    } else {
                        parsedFor(displayMessage)
                    }
                    val refTargets = if (displayMessage == post.message) {
                        conversation.refs.map { it.targetKey }
                    } else {
                        emptyList()
                    }
                    DisplayPost(
                        post = post,
                        parsed = displayParsed,
                        hidden = conversation.hidden,
                        marker = conversation.marker,
                        conversationColor = conversation.rootKey?.let(Conversation::colorIndex),
                        isMine = conversation.isMine,
                        isBigorno = conversation.isBigorno,
                        isReply = conversation.isReply,
                        refTargets = refTargets,
                    )
                }
                .toList()
        }

        if (selectedKey != null && display.none { it.post.key == selectedKey }) {
            selectedKey = null
        }

        val badges = HashMap<String, TribuneBadge>()
        for (tribune in current.tribunes) {
            var bigorno = false
            var reply = false
            val lastRead = prefs.lastReadPostId(tribune)
            for (post in posts) {
                if (post.tribune != tribune || post.id <= lastRead) continue
                val conversation = computed.posts[post.key] ?: continue
                if (conversation.isMine) continue
                if (conversation.hidden && current.displayMode == BlockedDisplayMode.Hide) continue
                bigorno = bigorno || conversation.isBigorno
                reply = reply || conversation.isReply
            }
            badges[tribune] = TribuneBadge(bigorno, reply)
        }

        _state.update {
            it.copy(
                displayPosts = display,
                badges = badges,
                selectedKey = selectedKey,
                serverBlocked = serverBlocked,
            )
        }
    }

    private fun parsedFor(message: String): ParsedMessage {
        if (parsedCache.size > 4_000) parsedCache.clear()
        return parsedCache.getOrPut(message) { MessageParser.parse(message) }
    }

    fun selectTribune(name: String) {
        selectTribuneInternal(name, markRead = true, clearSelection = true)
    }

    private fun selectTribuneInternal(name: String, markRead: Boolean, clearSelection: Boolean) {
        val current = _state.value
        if (name == current.activeTribune || name !in current.tribunes) return
        val previous = current.activeTribune
        if (markRead && previous != null) {
            val lastVisible = current.displayPosts.maxOfOrNull { it.post.id }
            if (lastVisible != null) prefs.setLastReadPostId(previous, lastVisible)
        }
        val oldDraft = current.draft
        val newText = if (previous != null) {
            MessageParser.rewriteBouchotSuffix(oldDraft.text, previous, name)
        } else {
            oldDraft.text
        }
        prefs.draft = newText
        val delta = newText.length - oldDraft.text.length
        val start = (oldDraft.selectionStart + delta).coerceIn(0, newText.length)
        val end = (oldDraft.selectionEnd + delta).coerceIn(0, newText.length)
        if (clearSelection) selectedKey = null
        _state.update {
            it.copy(
                activeTribune = name,
                draft = TextDraft(newText, start, end),
                selectedKey = selectedKey,
            )
        }
        recomputeNow()
    }

    fun selectNextTribune(direction: Int) {
        val current = _state.value
        if (current.tribunes.isEmpty()) return
        val size = current.tribunes.size
        val currentIndex = current.activeIndex.takeIf { it >= 0 } ?: 0
        val next = ((currentIndex + direction) % size + size) % size
        selectTribune(current.tribunes[next])
    }

    fun selectPost(key: String) {
        selectedKey = key.takeIf { candidate ->
            _state.value.displayPosts.any { it.post.key == candidate }
        }
        _state.update { it.copy(selectedKey = selectedKey) }
    }

    fun clearSelection() {
        selectedKey = null
        _state.update { it.copy(selectedKey = null) }
    }

    fun jumpToNorloge(fromPostKey: String, ref: MessageToken.Norloge) {
        val current = _state.value
        val conversation = analysis?.posts?.get(fromPostKey)
        val targetKey = conversation?.refs?.getOrNull(ref.index)?.targetKey
        if (targetKey == null) {
            emit("no post at ${ref.displayText}", isError = true)
            return
        }
        val target = allPosts[targetKey]
        if (target == null) {
            emit("post at ${ref.displayText} is no longer available", isError = true)
            return
        }
        val targetConversation = analysis?.posts?.get(targetKey)
        if (targetConversation?.hidden == true && current.displayMode == BlockedDisplayMode.Hide) {
            emit("post at ${ref.displayText} is hidden by the conversation filter", isError = true)
            return
        }
        if (target.tribune != current.activeTribune) {
            selectTribuneInternal(target.tribune, markRead = true, clearSelection = true)
        }
        selectedKey = targetKey
        _state.update {
            it.copy(selectedKey = targetKey, jumpTargetKey = targetKey)
        }
    }

    fun jumpHandled() {
        _state.update { it.copy(jumpTargetKey = null) }
    }

    fun callAuthor(post: Post) {
        val identity = post.login.ifBlank { post.info.ifBlank { "coward" } }
        insertSnippet("$identity<")
    }

    fun insertReplyClock(postKey: String) {
        val post = allPosts[postKey] ?: return
        val active = _state.value.activeTribune
        val suffix = if (post.tribune != active) "@${post.tribune}" else ""
        // A bare clock always means "today". Use the long form when the post is
        // older so the reference cannot resolve to today's post at that time.
        val today = java.time.LocalDate.now().toString()
        val clock = if (post.fullDate.isNotBlank() && post.fullDate != today) {
            "${post.fullDate}T${post.clock}"
        } else {
            post.clock
        }
        insertSnippet(clock + suffix)
    }

    fun updateDraft(draft: TextDraft) {
        setDraft(draft)
    }

    private fun setDraft(draft: TextDraft, focus: Boolean = false) {
        val coerced = draft.coerce()
        if (coerced.text.length > MAX_MESSAGE_LENGTH) return
        prefs.draft = coerced.text
        _state.update {
            it.copy(
                draft = coerced,
                focusComposerToken = if (focus) it.focusComposerToken + 1 else it.focusComposerToken,
            )
        }
    }

    private fun insertSnippet(snippet: String) {
        val draft = _state.value.draft.coerce()
        val before = draft.text.substring(0, draft.selectionStart)
        val after = draft.text.substring(draft.selectionEnd)
        val leading = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val value = leading + snippet + " "
        val newText = before + value + after
        val cursor = before.length + value.length
        setDraft(TextDraft(newText, cursor, cursor), focus = true)
    }

    fun insertTotoz(name: String) {
        insertSnippet("[:$name]")
    }

    fun insertEmoji(characters: String) {
        insertSnippet(characters)
    }

    fun insertAttachmentUrl(url: String) {
        insertSnippet(url)
    }

    fun applyAlt(name: String) {
        if (name == "lastPosted") {
            val lastPosted = _state.value.lastPosted ?: prefs.lastPostedMessage
            if (lastPosted.isBlank()) {
                emit("no previously posted message", isError = true)
                return
            }
            setDraft(TextDraft(lastPosted, lastPosted.length, lastPosted.length), focus = true)
            return
        }
        val template = ALT_TEMPLATES[name] ?: return
        val draft = _state.value.draft.coerce()
        val selected = draft.selectedText()
        val placeholder = template.indexOf("%s")
        val inserted = if (placeholder >= 0) template.replace("%s", selected) else template
        val before = draft.text.substring(0, draft.selectionStart)
        val after = draft.text.substring(draft.selectionEnd)
        val newText = before + inserted + after
        val cursor = if (placeholder >= 0) {
            draft.selectionStart + placeholder
        } else {
            draft.selectionStart + inserted.length
        }
        setDraft(TextDraft(newText, cursor, cursor), focus = true)
    }

    fun send() {
        val current = _state.value
        if (current.sending) return
        val tribune = current.activeTribune ?: return
        val message = current.draft.text.trim()
        if (message.isEmpty()) return
        val board = if (current.directBoards) {
            current.boards.firstOrNull { it.name == tribune }
        } else {
            null
        }
        if (current.directBoards && board == null) {
            emit("unknown board $tribune", isError = true)
            return
        }
        val needsLinuxfr = board?.requiresOAuth == true || (!current.directBoards && tribune == "dlfp")
        if (needsLinuxfr) {
            viewModelScope.launch {
                _state.update { it.copy(sending = true) }
                val token = ensureLinuxfrToken()
                if (token == null) {
                    if (current.directBoards && current.serverUrl.isBlank()) {
                        _state.update { it.copy(sending = false) }
                        emit(
                            "LinuxFR needs a gb3 server URL to authorize; set one in Settings",
                            isError = true,
                        )
                        return@launch
                    }
                    prefs.pendingLinuxfrMessage = message
                    clearDraft()
                    _state.update { it.copy(sending = false, screen = Screen.LinuxfrOauth) }
                    emit("connect your linuxfr account to post there")
                    return@launch
                }
                postMessage(tribune, message, token)
            }
        } else {
            postMessage(tribune, message, null)
        }
    }

    private fun postMessage(tribune: String, message: String, accessToken: String?) {
        val info = _state.value.nickname
        val board = if (_state.value.directBoards) {
            _state.value.boards.firstOrNull { it.name == tribune }
        } else {
            null
        }
        _state.update { it.copy(sending = true) }
        clearDraft()
        viewModelScope.launch {
            try {
                if (board != null) {
                    boardClient.post(board, info, message, accessToken)
                } else {
                    api.post(_state.value.serverUrl, tribune, info, message, accessToken)
                }
                prefs.lastPostedMessage = message
                _state.update { it.copy(sending = false, lastPosted = message) }
                emit("posted to $tribune")
                // gb2c polls immediately after a post; do the same so the new
                // message shows up without waiting for the next 30 second tick.
                if (board != null) refreshBoard(board)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(sending = false) }
                if (_state.value.draft.text.isBlank()) {
                    restoreDraft(message)
                }
                emit(error.message ?: "cannot post message", isError = true)
            }
        }
    }

    private fun clearDraft() {
        prefs.draft = ""
        _state.update { it.copy(draft = TextDraft()) }
    }

    private fun restoreDraft(text: String) {
        prefs.draft = text
        _state.update {
            it.copy(
                draft = TextDraft(text, text.length, text.length),
                focusComposerToken = it.focusComposerToken + 1,
            )
        }
    }

    private suspend fun ensureLinuxfrToken(): String? {
        val current = _state.value
        val session = prefs.linuxfrSession ?: return null
        if (session.accessToken.isNotBlank() &&
            session.expiresAt > System.currentTimeMillis() + 60_000
        ) {
            return session.accessToken
        }
        if (current.serverUrl.isBlank()) {
            prefs.linuxfrSession = null
            _state.update { it.copy(linuxfr = null) }
            emit("linuxfr session expired and no gb3 server is configured", isError = true)
            return null
        }
        if (session.refreshToken.isBlank()) {
            prefs.linuxfrSession = null
            _state.update { it.copy(linuxfr = null) }
            return null
        }
        return try {
            val refreshed = api.refreshLinuxfr(current.serverUrl, session.refreshToken)
                .copy(login = session.login)
            prefs.linuxfrSession = refreshed
            _state.update { it.copy(linuxfr = refreshed) }
            refreshed.accessToken
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            prefs.linuxfrSession = null
            _state.update { it.copy(linuxfr = null) }
            emit("linuxfr session expired: ${error.message ?: "reconnect"}", isError = true)
            null
        }
    }

    fun saveSettings(input: SettingsInput): Boolean {
        val current = _state.value
        val mode = input.connectionMode
        val modeChanged = mode != current.connectionMode
        val normalized = when {
            input.serverUrl.isBlank() -> null
            else -> Gb3Api.normalizeServerUrl(input.serverUrl)
        }
        if (mode == ConnectionMode.Server && normalized == null) {
            emit("invalid server URL", isError = true)
            return false
        }
        if (input.serverUrl.isNotBlank() && normalized == null) {
            emit("invalid server URL", isError = true)
            return false
        }
        val effectiveServer = normalized.orEmpty()
        val serverChanged = effectiveServer != current.serverUrl
        val nickname = input.nickname.trim().ifBlank {
            current.nickname.ifBlank { randomNickname() }
        }
        val blocked = input.blockedIdentities.csvSet()
        val plopList = input.plopList.csvSet()
        val available = when (mode) {
            ConnectionMode.DirectBoards -> current.boards.map { it.name }.sorted()
            ConnectionMode.Server -> current.availableTribunes
        }
        val requested = input.selectedTribunes.filter { it in available }
        val effectiveSelected = when (mode) {
            ConnectionMode.DirectBoards -> when {
                requested.isNotEmpty() -> requested.sorted()
                available.isNotEmpty() -> available
                else -> emptyList()
            }

            ConnectionMode.Server -> when {
                available.isEmpty() -> requested.toList()
                requested.isEmpty() -> available
                else -> requested.toList()
            }
        }

        val resetSelection = mode == ConnectionMode.Server && (serverChanged || modeChanged)
        prefs.serverUrl = effectiveServer
        prefs.connectionMode = mode
        prefs.nickname = nickname
        prefs.localBlockedIdentitiesCsv = blocked.joinToString(",")
        prefs.blockedDisplayMode = input.displayMode
        prefs.plopListCsv = plopList.joinToString(",")
        prefs.selectedTribunesCsv = if (resetSelection) "" else effectiveSelected.joinToString(",")
        emit("settings saved")

        _state.update {
            it.copy(
                serverUrl = effectiveServer,
                connectionMode = mode,
                nickname = nickname,
                localBlocked = blocked,
                displayMode = input.displayMode,
                plopList = plopList,
                screen = Screen.Main,
                selectedKey = selectedKey,
            )
        }

        if (modeChanged || (mode == ConnectionMode.Server && serverChanged)) {
            allPosts.clear()
            parsedCache.clear()
            plopOverrides.clear()
            selectedKey = null
            serverBlocked = emptySet()
            _state.update {
                it.copy(
                    serverBlocked = emptySet(),
                    availableTribunes = if (mode == ConnectionMode.DirectBoards) {
                        it.boards.map { board -> board.name }.sorted()
                    } else {
                        emptyList()
                    },
                    tribunes = emptyList(),
                    activeTribune = null,
                    displayPosts = emptyList(),
                    badges = emptyMap(),
                    selectedKey = null,
                    connected = false,
                    connecting = true,
                    initializing = true,
                    setupRequired = false,
                )
            }
            bootstrap(clearPosts = true)
            return true
        }

        val tribunesChanged = effectiveSelected.toSet() != current.tribunes.toSet()
        if (tribunesChanged) {
            val allowed = effectiveSelected.toSet()
            allPosts.keys.toList()
                .filter { key -> allPosts[key]?.tribune !in allowed }
                .forEach(allPosts::remove)
            selectedKey = null
            val active = current.activeTribune?.takeIf { it in effectiveSelected }
                ?: effectiveSelected.firstOrNull()
            _state.update {
                it.copy(
                    tribunes = effectiveSelected,
                    activeTribune = active,
                    selectedKey = null,
                )
            }
            if (foreground) {
                if (effectiveSelected.isEmpty()) {
                    stopPolling()
                    stopBoardPolling()
                } else if (mode == ConnectionMode.DirectBoards) {
                    stopPolling()
                    startBoardPolling(effectiveSelected, force = true)
                } else {
                    stopBoardPolling()
                    startPolling(effectiveServer, effectiveSelected, force = true)
                }
            } else {
                stopPolling()
                stopBoardPolling()
            }
        } else if (effectiveSelected.isEmpty()) {
            stopPolling()
            stopBoardPolling()
        }

        for (post in allPosts.values) {
            if (post.info in plopList && !plopOverrides.containsKey(post.key)) {
                plopOverrides[post.key] = PLOP_WORDS.random()
            }
        }
        plopOverrides.keys.removeAll { key -> allPosts[key]?.info !in plopList }
        recomputeNow()
        return true
    }

    fun openSettings() {
        _state.update { it.copy(screen = Screen.Settings) }
    }

    fun closeSettings() {
        _state.update { it.copy(screen = Screen.Main) }
    }

    /**
     * Add a user-defined board. Validation lives in [Board] so the same rules
     * apply to boards loaded from preferences.
     */
    fun addCustomBoard(input: Board): Boolean {
        val error = input.validationError()
        if (error != null) {
            emit("invalid board: $error", isError = true)
            return false
        }
        val current = _state.value
        val board = input.normalized()
        if (current.boards.any { it.name == board.name }) {
            emit("a board named ${board.name} already exists", isError = true)
            return false
        }
        val custom = current.boards.filter { !it.predefined } + board
        prefs.customBoardsJson = BoardCodec.encode(custom)
        val boards = PredefinedBoards.merge(custom)
        val names = boards.map { it.name }.sorted()
        val selected = if (current.directBoards) {
            (current.tribunes + board.name).distinct().sorted()
        } else {
            current.tribunes
        }
        if (current.directBoards) {
            prefs.selectedTribunesCsv = selected.joinToString(",")
        }
        _state.update {
            it.copy(
                boards = boards,
                availableTribunes = if (current.directBoards) names else it.availableTribunes,
                tribunes = selected,
                activeTribune = it.activeTribune ?: selected.firstOrNull(),
            )
        }
        if (current.directBoards) {
            recomputeNow()
            if (foreground) startBoardPolling(selected, force = true)
        }
        emit("board ${board.name} added")
        return true
    }

    fun removeCustomBoard(name: String): Boolean {
        val current = _state.value
        val board = current.boards.firstOrNull { it.name == name }
        if (board == null) return false
        if (board.predefined) {
            emit("built-in boards cannot be removed", isError = true)
            return false
        }
        val custom = current.boards.filter { !it.predefined && it.name != name }
        prefs.customBoardsJson = BoardCodec.encode(custom)
        val boards = PredefinedBoards.merge(custom)
        val names = boards.map { it.name }.sorted()
        val selected = current.tribunes.filter { it != name }
        allPosts.keys.toList()
            .filter { key -> allPosts[key]?.tribune == name }
            .forEach(allPosts::remove)
        val active = current.activeTribune
            ?.takeIf { it != name && it in selected }
            ?: selected.firstOrNull()
        if (current.directBoards) {
            prefs.selectedTribunesCsv = selected.joinToString(",")
        }
        _state.update {
            it.copy(
                boards = boards,
                availableTribunes = if (current.directBoards) names else it.availableTribunes,
                tribunes = selected,
                activeTribune = active,
            )
        }
        if (current.directBoards) {
            recomputeNow()
            if (foreground) {
                if (selected.isEmpty()) {
                    stopBoardPolling()
                } else {
                    startBoardPolling(selected, force = true)
                }
            }
        }
        emit("board $name removed")
        return true
    }

    fun openLinuxfrOauth() {
        val current = _state.value
        if (current.serverUrl.isBlank()) {
            emit("set a gb3 server URL to connect LinuxFR", isError = true)
            return
        }
        _state.update { it.copy(screen = Screen.LinuxfrOauth) }
    }

    fun onLinuxfrConnected(session: LinuxfrSession) {
        prefs.linuxfrSession = session
        val pending = prefs.pendingLinuxfrMessage
        prefs.pendingLinuxfrMessage = null
        val current = _state.value
        val target = if ("dlfp" in current.tribunes) "dlfp" else current.activeTribune
        val draft = if (pending != null) {
            TextDraft(pending, pending.length, pending.length)
        } else {
            current.draft
        }
        prefs.draft = draft.text
        _state.update {
            it.copy(
                linuxfr = session,
                screen = Screen.Main,
                activeTribune = target,
                draft = draft,
                focusComposerToken = it.focusComposerToken + 1,
            )
        }
        recomputeNow()
        emit("connected to linuxfr as ${session.login}")
    }

    fun onLinuxfrOauthCancelled() {
        val pending = prefs.pendingLinuxfrMessage
        prefs.pendingLinuxfrMessage = null
        _state.update { it.copy(screen = Screen.Main) }
        if (pending != null) restoreDraft(pending)
    }

    fun attach(uri: Uri) {
        val current = _state.value
        if (current.uploading) return
        if (current.serverUrl.isBlank()) {
            emit("attachments need a gb3 server URL", isError = true)
            return
        }
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            try {
                val location = api.uploadAttachment(
                    current.serverUrl,
                    getApplication<Application>().contentResolver,
                    uri,
                )
                insertAttachmentUrl(location)
                emit("attachment uploaded")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(error.message ?: "cannot upload attachment", isError = true)
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    suspend fun searchTotoz(terms: String): Result<List<Totoz>> {
        val baseUrl = _state.value.serverUrl
        if (baseUrl.isBlank()) {
            return Result.failure(IOException("totoz search needs a gb3 server URL"))
        }
        return try {
            Result.success(api.searchTotoz(baseUrl, terms))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun searchEmoji(terms: String): Result<List<EmojiItem>> {
        val baseUrl = _state.value.serverUrl
        if (baseUrl.isBlank()) {
            return Result.failure(IOException("emoji search needs a gb3 server URL"))
        }
        return try {
            Result.success(api.searchEmoji(baseUrl, terms))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun markAllRead() {
        val maxIds = analysis?.maxIdByTribune ?: allPosts.values.groupBy { it.tribune }
            .mapValues { (_, posts) -> posts.maxOf { it.id } }
        prefs.markAllRead(_state.value.tribunes, maxIds)
        recomputeNow()
        emit("all tribunes marked as read")
    }

    fun refreshServerInfo() {
        if (!foreground) {
            emit("cannot refresh while the app is in the background", isError = true)
            return
        }
        bootstrap()
    }

    fun disconnectLinuxfr() {
        prefs.linuxfrSession = null
        _state.update { it.copy(linuxfr = null) }
        recomputeNow()
        emit("linuxfr account disconnected")
    }

    private fun emit(message: String, isError: Boolean = false) {
        _events.trySend(UiEvent.Message(message, isError))
    }
}
