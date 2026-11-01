package org.zorel.gb3android.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.zorel.gb3android.R
import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.BoardClient
import org.zorel.gb3android.data.BoardCodec
import org.zorel.gb3android.data.BoardValidationError
import org.zorel.gb3android.data.EmojiItem
import org.zorel.gb3android.data.Gb2cServer
import org.zorel.gb3android.data.Gb3Api
import org.zorel.gb3android.data.LinuxfrSession
import org.zorel.gb3android.data.PollEvent
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Preferences
import org.zorel.gb3android.data.PredefinedBoards
import org.zorel.gb3android.data.Screen
import org.zorel.gb3android.data.SourceId
import org.zorel.gb3android.data.SourceStatus
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.data.Totoz
import org.zorel.gb3android.data.TotozServer
import org.zorel.gb3android.data.TribuneBadge
import org.zorel.gb3android.data.TribuneKey
import org.zorel.gb3android.data.csvSet
import org.zorel.gb3android.data.randomNickname
import org.zorel.gb3android.domain.Conversation
import org.zorel.gb3android.domain.MessageParser
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.domain.ParsedMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val gb2cPollJobs = HashMap<String, Job>()
    private val boardPollJobs = HashMap<String, Job>()
    private var recomputeJob: Job? = null
    private val gb2cStatus = HashMap<String, SourceStatus>()
    private val boardStatus = HashMap<String, SourceStatus>()
    private val serverBlockedById = HashMap<String, Set<String>>()
    private val reportedGb2cFailures = HashSet<String>()
    private val reportedBoardFailures = HashSet<String>()
    private val knownTribunes = HashMap<String, List<String>>()

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
        val boards = PredefinedBoards.merge(BoardCodec.decode(prefs.customBoardsJson))
        val boardNames = boards.mapTo(HashSet()) { it.name }
        val servers = prefs.loadGb2cServers()
        val selectedBoards = prefs.loadSelectedBoards(boardNames)
        knownTribunes.putAll(prefs.loadKnownTribunes())
        val tribunes = buildTribunes(servers, selectedBoards, boards)
        val storedActive = prefs.activeTribuneKey?.let(TribuneKey::fromKey)
        val active = storedActive?.takeIf { candidate ->
            tribunes.any { it.key == candidate.key }
        } ?: tribunes.firstOrNull()
        val hasSource = servers.any { it.enabled } || selectedBoards.isNotEmpty()
        _state.value = UiState(
            serverUrl = servers.firstOrNull { it.enabled }?.url ?: prefs.serverUrl,
            gb2cServers = servers,
            boards = boards,
            selectedBoards = selectedBoards,
            totozServerUrl = prefs.totozServerUrl,
            nickname = nickname,
            tribunes = tribunes,
            activeTribune = active,
            localBlocked = prefs.localBlockedIdentitiesCsv.csvSet(),
            displayMode = prefs.blockedDisplayMode,
            plopList = prefs.plopListCsv.csvSet(),
            draft = TextDraft(draftText, draftText.length, draftText.length),
            linuxfr = prefs.linuxfrSession,
            lastPosted = prefs.lastPostedMessage.ifBlank { null },
            setupRequired = !hasSource,
            initializing = hasSource,
        )
        prefs.activeTribuneKey = active?.key
        if (hasSource) bootstrap()
    }

    fun onForeground() {
        foreground = true
        val current = _state.value
        if (current.setupRequired) return
        if (bootstrapJob?.isActive == true) return
        if (current.tribunes.isEmpty()) {
            bootstrap()
        } else {
            syncPolling()
        }
    }

    fun onBackground() {
        foreground = false
        stopAllPolling()
    }

    fun reconnect() {
        val current = _state.value
        if (current.tribunes.isEmpty()) {
            emit(tr(R.string.error_no_tribune_server), isError = true)
            return
        }
        if (!foreground) {
            emit(tr(R.string.error_reconnect_stream_background), isError = true)
            return
        }
        if (bootstrapJob?.isActive == true) return
        emit(tr(R.string.msg_reconnecting))
        syncPolling(force = true)
    }

    private fun buildTribunes(
        servers: List<Gb2cServer>,
        selectedBoards: Set<String>,
        boards: List<Board>,
    ): List<TribuneKey> {
        val result = ArrayList<TribuneKey>()
        for (server in servers) {
            if (!server.enabled) continue
            for (name in knownTribunes[server.url].orEmpty().sorted()) {
                result += TribuneKey(SourceId.Gb2c(server.url), name)
            }
        }
        val boardNames = boards.mapTo(HashSet()) { it.name }
        for (name in selectedBoards.filter { it in boardNames }.sorted()) {
            result += TribuneKey(SourceId.Direct(name), name)
        }
        return result
    }

    private fun chooseActiveTribune(
        current: TribuneKey?,
        tribunes: List<TribuneKey>,
        pendingDraft: String?,
    ): TribuneKey? {
        if (pendingDraft != null) {
            tribunes.firstOrNull { it.name == "dlfp" }?.let { return it }
        }
        return current?.takeIf { candidate -> tribunes.any { it.key == candidate.key } }
            ?: tribunes.firstOrNull()
    }

    private fun bootstrap(clearPosts: Boolean = false) {
        bootstrapJob?.cancel()
        stopAllPolling()
        if (clearPosts) {
            allPosts.clear()
            parsedCache.clear()
            plopOverrides.clear()
            selectedKey = null
        }
        val current = _state.value
        val enabledServers = current.gb2cServers.filter { it.enabled }
        val selectedBoards = current.selectedBoards
        if (enabledServers.isEmpty() && selectedBoards.isEmpty()) {
            _state.update {
                it.copy(
                    initializing = false,
                    connecting = false,
                    connected = false,
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
            return
        }

        _state.update { it.copy(initializing = true, setupRequired = false) }
        bootstrapJob = viewModelScope.launch {
            val results = coroutineScope {
                enabledServers.map { server ->
                    async {
                        val names = try {
                            Result.success(api.tribunes(server.url))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Result.failure(error)
                        }
                        val blocked = try {
                            Result.success(api.blockedIdentities(server.url))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Result.failure(error)
                        }
                        Triple(server, names, blocked)
                    }
                }.awaitAll()
            }

            val known = HashMap(knownTribunes)
            val blockedByServer = HashMap(serverBlockedById)
            blockedByServer.keys.retainAll(enabledServers.mapTo(HashSet()) { it.url })
            for ((server, namesResult, blockedResult) in results) {
                if (namesResult.isSuccess) {
                    known[server.url] = namesResult.getOrThrow().sorted()
                    reportedGb2cFailures.remove(server.url)
                    if (gb2cStatus[server.url] == null) {
                        gb2cStatus[server.url] = SourceStatus.Working
                    }
                } else {
                    gb2cStatus[server.url] = SourceStatus.Error
                    if (reportedGb2cFailures.add(server.url)) {
                        emit(
                            tr(
                                R.string.error_list_tribunes,
                                namesResult.exceptionOrNull()?.message
                                    ?: tr(R.string.error_network),
                            ),
                            isError = true,
                        )
                    }
                }
                blockedResult.onSuccess { blockedByServer[server.url] = it.toSet() }
                if (blockedResult.isFailure && clearPosts) {
                    blockedByServer.remove(server.url)
                }
            }
            knownTribunes.clear()
            knownTribunes.putAll(known)
            prefs.saveKnownTribunes(known)
            serverBlockedById.clear()
            serverBlockedById.putAll(blockedByServer)
            serverBlocked = blockedByServer.values.flatten().toSet()

            val latest = _state.value
            val tribunes = buildTribunes(latest.gb2cServers, latest.selectedBoards, latest.boards)
            val active = chooseActiveTribune(latest.activeTribune, tribunes, pendingDraftOnStart)
            pendingDraftOnStart = null
            selectedKey = null
            _state.update {
                it.copy(
                    tribunes = tribunes,
                    activeTribune = active,
                    initializing = false,
                    setupRequired = false,
                    serverBlocked = serverBlocked,
                    selectedKey = null,
                )
            }
            prefs.activeTribuneKey = active?.key
            recomputeNow()
            if (foreground) {
                syncPolling(force = true)
            } else {
                publishStatus()
            }
            if (tribunes.isEmpty() && latest.gb2cServers.any { it.enabled }) {
                emit(tr(R.string.error_no_tribune_server), isError = true)
            }
        }
    }

    /**
     * Keep one gb2c stream per enabled server and one loop per selected board,
     * so a slow source cannot delay the others.
     */
    private fun syncPolling(force: Boolean = false) {
        val current = _state.value
        val desiredGb2c = LinkedHashMap<String, List<String>>()
        for (server in current.gb2cServers) {
            if (!server.enabled) continue
            val names = current.tribunes.asSequence()
                .filter { it.source == SourceId.Gb2c(server.url) }
                .map { it.name }
                .sorted()
                .toList()
            if (names.isNotEmpty()) desiredGb2c[server.url] = names
        }
        val desiredBoards = current.tribunes.asSequence()
            .mapNotNull { (it.source as? SourceId.Direct)?.board }
            .filter { name -> current.boards.any { it.name == name } }
            .distinct()
            .sorted()
            .toList()

        val gb2cReady = !force &&
            gb2cPollJobs.keys == desiredGb2c.keys &&
            gb2cPollJobs.values.all { it.isActive }
        val boardsReady = !force &&
            boardPollJobs.keys == desiredBoards.toSet() &&
            boardPollJobs.values.all { it.isActive }
        if (gb2cReady && boardsReady) return

        stopAllPolling()
        for ((url, names) in desiredGb2c) {
            startGb2cPolling(url, names)
        }
        for (name in desiredBoards) {
            current.boards.firstOrNull { it.name == name }?.let(::startBoardPolling)
        }
        publishStatus()
    }

    private fun startGb2cPolling(url: String, tribunes: List<String>) {
        if (tribunes.isEmpty()) return
        gb2cStatus[url] = SourceStatus.Working
        publishStatus()
        val job = viewModelScope.launch {
            try {
                api.poll(url, tribunes).collect { event ->
                    when (event) {
                        PollEvent.Connecting -> {
                            gb2cStatus[url] = SourceStatus.Working
                            publishStatus()
                        }

                        PollEvent.Connected -> {
                            gb2cStatus[url] = SourceStatus.Online
                            reportedGb2cFailures.remove(url)
                            publishStatus()
                        }

                        is PollEvent.NewPost -> ingest(event.post)

                        is PollEvent.Failed -> {
                            gb2cStatus[url] = SourceStatus.Error
                            publishStatus()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                gb2cStatus[url] = SourceStatus.Error
                publishStatus()
                if (reportedGb2cFailures.add(url)) {
                    emit(error.message ?: tr(R.string.error_poll_stopped), isError = true)
                }
            }
        }
        gb2cPollJobs[url] = job
    }

    private fun stopAllPolling() {
        gb2cPollJobs.values.forEach { it.cancel() }
        gb2cPollJobs.clear()
        boardPollJobs.values.forEach { it.cancel() }
        boardPollJobs.clear()
        gb2cStatus.clear()
        boardStatus.clear()
        reportedBoardFailures.clear()
        publishStatus()
    }

    private fun publishStatus() {
        val statuses = gb2cStatus.values + boardStatus.values
        _state.update {
            it.copy(
                gb2cStatus = gb2cStatus.toMap(),
                boardStatus = boardStatus.toMap(),
                connected = statuses.any { status -> status == SourceStatus.Online },
                connecting = statuses.any { status -> status == SourceStatus.Working },
            )
        }
    }

    /** Poll one selected board at gb2c's usual 30 second interval. */
    private fun startBoardPolling(board: Board) {
        boardStatus[board.name] = SourceStatus.Working
        publishStatus()
        val job = viewModelScope.launch {
            while (isActive) {
                val started = System.currentTimeMillis()
                refreshBoard(board)
                val elapsed = System.currentTimeMillis() - started
                delay((BOARD_POLL_INTERVAL_MS - elapsed).coerceAtLeast(1_000L))
            }
        }
        boardPollJobs[board.name] = job
    }

    /** Fetch one board once and update its connection indicator. */
    private suspend fun refreshBoard(board: Board) {
        try {
            val posts = boardClient.fetch(board)
            posts.forEach(::ingest)
            boardStatus[board.name] = SourceStatus.Online
            reportedBoardFailures.remove(board.name)
            publishStatus()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            boardStatus[board.name] = SourceStatus.Error
            publishStatus()
            if (reportedBoardFailures.add(board.name)) {
                emit(
                    tr(
                        R.string.error_cannot_read_board,
                        board.name,
                        error.message ?: tr(R.string.error_network),
                    ),
                    isError = true,
                )
            }
        }
    }

    private fun ingest(post: Post) {
        val tribuneKey = post.tribuneKey
        if (_state.value.tribunes.none { it.key == tribuneKey.key }) return
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
                .filter { it.tribuneKey == active }
                .sortedBy { it.id }
                .mapNotNull { post ->
                    val conversation = computed.posts[post.key] ?: return@mapNotNull null
                    if (conversation.hidden && current.displayMode == BlockedDisplayMode.Hide) {
                        return@mapNotNull null
                    }
                    val displayMessage = when {
                        conversation.hidden -> tr(R.string.msg_hidden_post)
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
            val lastRead = prefs.lastReadPostId(tribune.key)
            for (post in posts) {
                if (post.tribuneKey != tribune || post.id <= lastRead) continue
                val conversation = computed.posts[post.key] ?: continue
                if (conversation.isMine) continue
                if (conversation.hidden && current.displayMode == BlockedDisplayMode.Hide) continue
                bigorno = bigorno || conversation.isBigorno
                reply = reply || conversation.isReply
            }
            badges[tribune.key] = TribuneBadge(bigorno, reply)
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

    fun selectTribune(key: String) {
        selectTribuneInternal(key, markRead = true, clearSelection = true)
    }

    private fun selectTribuneInternal(key: String, markRead: Boolean, clearSelection: Boolean) {
        val current = _state.value
        val target = current.tribunes.firstOrNull { it.key == key } ?: return
        if (target.key == current.activeTribune?.key) return
        val previous = current.activeTribune
        if (markRead && previous != null) {
            val lastVisible = current.displayPosts.maxOfOrNull { it.post.id }
            if (lastVisible != null) prefs.setLastReadPostId(previous.key, lastVisible)
        }
        val oldDraft = current.draft
        val newText = if (previous != null && previous.name != target.name) {
            MessageParser.rewriteBouchotSuffix(oldDraft.text, previous.name, target.name)
        } else {
            oldDraft.text
        }
        prefs.draft = newText
        prefs.activeTribuneKey = target.key
        val delta = newText.length - oldDraft.text.length
        val start = (oldDraft.selectionStart + delta).coerceIn(0, newText.length)
        val end = (oldDraft.selectionEnd + delta).coerceIn(0, newText.length)
        if (clearSelection) selectedKey = null
        _state.update {
            it.copy(
                activeTribune = target,
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
        selectTribune(current.tribunes[next].key)
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
            emit(tr(R.string.error_no_post_at, ref.displayText), isError = true)
            return
        }
        val target = allPosts[targetKey]
        if (target == null) {
            emit(tr(R.string.error_post_gone, ref.displayText), isError = true)
            return
        }
        val targetConversation = analysis?.posts?.get(targetKey)
        if (targetConversation?.hidden == true && current.displayMode == BlockedDisplayMode.Hide) {
            emit(tr(R.string.error_post_hidden, ref.displayText), isError = true)
            return
        }
        if (target.tribuneKey.key != current.activeKey) {
            selectTribuneInternal(target.tribuneKey.key, markRead = true, clearSelection = true)
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
        val suffix = if (post.tribuneKey.name != active?.name) "@${post.tribune}" else ""
        // A bare clock always means "today". Use the long form when the post is
        // older so the reference cannot resolve to today's post at that time.
        // The space separator is the readable 8601 variant accepted by gb3.
        val today = java.time.LocalDate.now().toString()
        val clock = if (post.fullDate.isNotBlank() && post.fullDate != today) {
            "${post.fullDate} ${post.clock}"
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
                emit(tr(R.string.error_no_previous_message), isError = true)
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
        val board = (tribune.source as? SourceId.Direct)?.let { direct ->
            current.boards.firstOrNull { it.name == direct.board }
        }
        if (tribune.source is SourceId.Direct && board == null) {
            emit(tr(R.string.error_unknown_board, tribune.name), isError = true)
            return
        }
        val needsLinuxfr = board?.requiresOAuth == true ||
            (tribune.source is SourceId.Gb2c && tribune.name == "dlfp")
        if (needsLinuxfr) {
            viewModelScope.launch {
                _state.update { it.copy(sending = true) }
                val token = ensureLinuxfrToken()
                if (token == null) {
                    if (tribune.source is SourceId.Direct && current.serverUrl.isBlank()) {
                        _state.update { it.copy(sending = false) }
                        emit(tr(R.string.error_linuxfr_needs_server), isError = true)
                        return@launch
                    }
                    prefs.pendingLinuxfrMessage = message
                    clearDraft()
                    _state.update { it.copy(sending = false, screen = Screen.LinuxfrOauth) }
                    emit(tr(R.string.msg_connect_linuxfr))
                    return@launch
                }
                postMessage(tribune, message, token)
            }
        } else {
            postMessage(tribune, message, null)
        }
    }

    private fun postMessage(tribune: TribuneKey, message: String, accessToken: String?) {
        val info = _state.value.nickname
        val board = (tribune.source as? SourceId.Direct)?.let { direct ->
            _state.value.boards.firstOrNull { it.name == direct.board }
        }
        _state.update { it.copy(sending = true) }
        clearDraft()
        viewModelScope.launch {
            try {
                when (val source = tribune.source) {
                    is SourceId.Gb2c ->
                        api.post(source.url, tribune.name, info, message, accessToken)

                    is SourceId.Direct ->
                        board?.let { boardClient.post(it, info, message, accessToken) }
                            ?: throw IOException("unknown board ${source.board}")
                }
                prefs.lastPostedMessage = message
                _state.update { it.copy(sending = false, lastPosted = message) }
                emit(tr(R.string.msg_posted_to, tribune.name))
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
                emit(error.message ?: tr(R.string.error_cannot_post), isError = true)
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
            emit(tr(R.string.error_linuxfr_expired_no_server), isError = true)
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
            emit(
                tr(
                    R.string.error_linuxfr_expired,
                    error.message ?: tr(R.string.error_reconnect),
                ),
                isError = true,
            )
            null
        }
    }

    fun saveSettings(input: SettingsInput): Boolean {
        val current = _state.value
        val servers = input.gb2cServers.map { it.normalized() }
        if (servers.any { it.validationError() != null }) {
            emit(tr(R.string.error_invalid_server), isError = true)
            return false
        }
        if (servers.groupBy { it.url }.any { (_, sameUrl) -> sameUrl.size > 1 }) {
            emit(tr(R.string.error_duplicate_server), isError = true)
            return false
        }
        val selectedBoards = input.selectedBoards
            .filter { name -> current.boards.any { it.name == name } }
            .toSet()
        if (servers.none { it.enabled } && selectedBoards.isEmpty()) {
            emit(tr(R.string.error_no_source), isError = true)
            return false
        }
        val totozServer = if (input.totozServerUrl.isBlank()) {
            ""
        } else {
            TotozServer.normalizeUrl(input.totozServerUrl) ?: run {
                emit(tr(R.string.error_invalid_totoz_server), isError = true)
                return false
            }
        }
        val nickname = input.nickname.trim().ifBlank {
            current.nickname.ifBlank { randomNickname() }
        }
        val blocked = input.blockedIdentities.csvSet()
        val plopList = input.plopList.csvSet()
        val servicesServer = servers.firstOrNull { it.enabled }?.url ?: current.serverUrl
        val sourcesChanged = servers != current.gb2cServers ||
            selectedBoards != current.selectedBoards

        prefs.saveGb2cServers(servers)
        prefs.saveSelectedBoards(selectedBoards)
        prefs.serverUrl = servicesServer
        prefs.totozServerUrl = totozServer
        prefs.nickname = nickname
        prefs.localBlockedIdentitiesCsv = blocked.joinToString(",")
        prefs.blockedDisplayMode = input.displayMode
        prefs.plopListCsv = plopList.joinToString(",")
        emit(tr(R.string.msg_settings_saved))

        _state.update {
            it.copy(
                serverUrl = servicesServer,
                gb2cServers = servers,
                selectedBoards = selectedBoards,
                totozServerUrl = totozServer,
                nickname = nickname,
                localBlocked = blocked,
                displayMode = input.displayMode,
                plopList = plopList,
                screen = Screen.Main,
                selectedKey = selectedKey,
            )
        }

        if (sourcesChanged) {
            allPosts.clear()
            parsedCache.clear()
            plopOverrides.clear()
            selectedKey = null
            serverBlocked = emptySet()
            serverBlockedById.clear()
            gb2cStatus.clear()
            boardStatus.clear()
            _state.update {
                it.copy(
                    tribunes = emptyList(),
                    activeTribune = null,
                    displayPosts = emptyList(),
                    badges = emptyMap(),
                    selectedKey = null,
                    connected = false,
                    connecting = false,
                    initializing = true,
                    setupRequired = false,
                    serverBlocked = emptySet(),
                    gb2cStatus = emptyMap(),
                    boardStatus = emptyMap(),
                )
            }
            prefs.activeTribuneKey = null
            bootstrap(clearPosts = true)
            return true
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
            emit(tr(R.string.error_invalid_board, boardValidationText(error)), isError = true)
            return false
        }
        val current = _state.value
        val board = input.normalized()
        if (current.boards.any { it.name == board.name }) {
            emit(tr(R.string.error_board_exists, board.name), isError = true)
            return false
        }
        val custom = current.boards.filter { !it.predefined } + board
        prefs.customBoardsJson = BoardCodec.encode(custom)
        val boards = PredefinedBoards.merge(custom)
        val selectedBoards = current.selectedBoards + board.name
        prefs.saveSelectedBoards(selectedBoards)
        val tribunes = buildTribunes(current.gb2cServers, selectedBoards, boards)
        val active = current.activeTribune
            ?.takeIf { candidate -> tribunes.any { it.key == candidate.key } }
            ?: tribunes.firstOrNull()
        prefs.activeTribuneKey = active?.key
        _state.update {
            it.copy(
                boards = boards,
                selectedBoards = selectedBoards,
                tribunes = tribunes,
                activeTribune = active,
                setupRequired = false,
            )
        }
        if (foreground) {
            startBoardPolling(board)
        }
        recomputeNow()
        emit(tr(R.string.msg_board_added, board.name))
        return true
    }

    fun removeCustomBoard(name: String): Boolean {
        val current = _state.value
        val board = current.boards.firstOrNull { it.name == name }
        if (board == null) return false
        if (board.predefined) {
            emit(tr(R.string.error_builtin_board), isError = true)
            return false
        }
        val custom = current.boards.filter { !it.predefined && it.name != name }
        prefs.customBoardsJson = BoardCodec.encode(custom)
        val boards = PredefinedBoards.merge(custom)
        val selectedBoards = current.selectedBoards - name
        prefs.saveSelectedBoards(selectedBoards)
        val direct = TribuneKey(SourceId.Direct(name), name)
        allPosts.keys.toList()
            .filter { key -> allPosts[key]?.tribuneKey == direct }
            .forEach(allPosts::remove)
        boardPollJobs.remove(name)?.cancel()
        boardStatus.remove(name)
        val tribunes = buildTribunes(current.gb2cServers, selectedBoards, boards)
        val active = current.activeTribune
            ?.takeIf { candidate -> tribunes.any { it.key == candidate.key } }
            ?: tribunes.firstOrNull()
        prefs.activeTribuneKey = active?.key
        _state.update {
            it.copy(
                boards = boards,
                selectedBoards = selectedBoards,
                tribunes = tribunes,
                activeTribune = active,
                setupRequired = current.gb2cServers.none { server -> server.enabled } &&
                    selectedBoards.isEmpty(),
            )
        }
        publishStatus()
        recomputeNow()
        emit(tr(R.string.msg_board_removed, name))
        return true
    }

    fun openLinuxfrOauth() {
        val current = _state.value
        if (current.serverUrl.isBlank()) {
            emit(tr(R.string.error_connect_linuxfr_server), isError = true)
            return
        }
        _state.update { it.copy(screen = Screen.LinuxfrOauth) }
    }

    fun onLinuxfrConnected(session: LinuxfrSession) {
        prefs.linuxfrSession = session
        val pending = prefs.pendingLinuxfrMessage
        prefs.pendingLinuxfrMessage = null
        val current = _state.value
        val target = current.activeTribune?.takeIf { it.name == "dlfp" }
            ?: current.tribunes.firstOrNull { it.name == "dlfp" }
            ?: current.activeTribune
        val draft = if (pending != null) {
            TextDraft(pending, pending.length, pending.length)
        } else {
            current.draft
        }
        prefs.draft = draft.text
        prefs.activeTribuneKey = target?.key
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
        emit(tr(R.string.msg_linuxfr_connected, session.login))
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
            emit(tr(R.string.error_attachment_needs_server), isError = true)
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
                emit(tr(R.string.msg_attachment_uploaded))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(error.message ?: tr(R.string.error_upload_attachment), isError = true)
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    suspend fun searchTotoz(terms: String): Result<List<Totoz>> {
        val current = _state.value
        val totozServer = current.totozServerUrl.trim()
        val baseUrl = current.serverUrl
        return try {
            when {
                totozServer.isNotBlank() ->
                    Result.success(api.searchTotozDirect(totozServer, terms))
                baseUrl.isNotBlank() ->
                    Result.success(api.searchTotoz(baseUrl, terms))
                else ->
                    Result.success(api.searchTotozDirect(TotozServer.DEFAULT_URL, terms))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun searchEmoji(terms: String): Result<List<EmojiItem>> {
        val baseUrl = _state.value.serverUrl
        if (baseUrl.isBlank()) {
            return Result.failure(IOException(tr(R.string.error_emoji_needs_server)))
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
        val maxIds = analysis?.maxIdByTribune ?: allPosts.values
            .groupBy { it.tribuneKey.key }
            .mapValues { (_, posts) -> posts.maxOf { it.id } }
        prefs.markAllRead(_state.value.tribunes.map { it.key }, maxIds)
        recomputeNow()
        emit(tr(R.string.msg_all_read))
    }

    fun refreshServerInfo() {
        if (!foreground) {
            emit(tr(R.string.error_refresh_background), isError = true)
            return
        }
        bootstrap()
    }

    fun disconnectLinuxfr() {
        prefs.linuxfrSession = null
        _state.update { it.copy(linuxfr = null) }
        recomputeNow()
        emit(tr(R.string.msg_linuxfr_disconnected))
    }

    private fun boardValidationText(error: BoardValidationError): String = when (error) {
        BoardValidationError.NameRequired -> tr(R.string.board_error_name_required)
        BoardValidationError.NameTooLong -> tr(R.string.board_error_name_too_long)
        BoardValidationError.NameInvalid -> tr(R.string.board_error_name_invalid)
        BoardValidationError.BackendUrl -> tr(R.string.board_error_backend_url)
        BoardValidationError.PostUrl -> tr(R.string.board_error_post_url)
        BoardValidationError.PostField -> tr(R.string.board_error_post_field)
        BoardValidationError.InfoField -> tr(R.string.board_error_info_field)
    }

    private fun tr(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun emit(message: String, isError: Boolean = false) {
        _events.trySend(UiEvent.Message(message, isError))
    }
}
