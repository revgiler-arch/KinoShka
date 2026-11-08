package com.kinoshka.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kinoshka.app.App
import com.kinoshka.app.data.Kinopoisk
import com.kinoshka.app.data.Movie
import com.kinoshka.app.data.Platform
import com.kinoshka.app.data.Prefs
import com.kinoshka.app.data.Source
import com.kinoshka.app.player.PlayerActivity

@Composable
fun KinoApp(vm: MainVm) {
    KinoTheme {
        BackHandler(enabled = vm.stack.size > 1) { vm.back() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Kino.Bg)
                .safeDrawingPadding(),
        ) {
            when (val s = vm.screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Search -> SearchScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.Details -> DetailsScreen(vm, s.movie)
            }
        }
    }
}

// ---------------------------------------------------------------- Главная

@Composable
private fun HomeScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Кино", color = Kino.Text, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Шка", color = Kino.Accent, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                KButton("KinoGo", { ctx.startActivity(android.content.Intent(ctx, com.kinoshka.app.KinoGoActivity::class.java)) }, primary = true)
                Spacer(Modifier.width(8.dp))
                KButton("Поиск", { vm.open(Screen.Search) }, icon = Icons.Filled.Search)
                Spacer(Modifier.width(10.dp))
                KButton("", { vm.open(Screen.Settings) }, icon = Icons.Filled.Settings)
            }
        }
        vm.update?.let {
            item {
                Row(
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()
                        .background(Kino.Surface, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vm.updateStatus ?: "Доступна новая версия",
                        color = Kino.Text, modifier = Modifier.weight(1f),
                    )
                    KButton("Обновить", vm::installUpdate, icon = Icons.Filled.SystemUpdate, primary = true)
                }
            }
        }
        vm.crash?.let { text ->
            item { CrashCard(text, vm::clearCrash) }
        }
        if (!vm.hasKey) {
            item { WelcomeCard(onSettings = { vm.open(Screen.Settings) }) }
            return@LazyColumn
        }
        if (vm.history.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Продолжить просмотр")
                    MovieRow(vm.history, vm, firstFocus)
                }
            }
        }
        Kinopoisk.COLLECTIONS.forEachIndexed { i, c ->
            item(key = c.key) { Column {
                SectionTitle(c.title)
                when (val r = vm.rows[c.key]) {
                    is Load.Ok -> MovieRow(r.value, vm, if (i == 0 && vm.history.isEmpty()) firstFocus else null)
                    is Load.Fail -> Row(
                        Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.message, color = Kino.TextDim, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        KButton("Повторить", vm::retryRows, icon = Icons.Filled.Refresh)
                    }
                    else -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        CircularProgressIndicator(color = Kino.Accent)
                    }
                }
            } }
        }
    }
    // На телевизоре сразу ставим фокус на первый постер, чтобы пульт работал без «поиска» курсора
    LaunchedEffect(vm.rows[Kinopoisk.COLLECTIONS.first().key] is Load.Ok, vm.history.size) {
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun MovieRow(list: List<Movie>, vm: MainVm, firstFocus: FocusRequester?) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(list, key = { it.id }) { m ->
            val mod = if (firstFocus != null && m == list.first()) Modifier.focusRequester(firstFocus) else Modifier
            PosterCard(m, 132.dp, onClick = { vm.open(Screen.Details(m)) }, modifier = mod)
        }
    }
}

@Composable
private fun WelcomeCard(onSettings: () -> Unit) {
    Column(
        Modifier.padding(24.dp).fillMaxWidth()
            .background(Kino.Surface, RoundedCornerShape(16.dp)).padding(24.dp),
    ) {
        Text("Добро пожаловать!", color = Kino.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Каталог фильмов берётся с Кинопоиска через сервис kinopoisk.dev. " +
                "Нужен бесплатный ключ: напишите в Telegram боту @kinopoiskdev_bot, " +
                "получите ключ и вставьте его в настройках.",
            color = Kino.TextDim, fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        KButton("Открыть настройки", onSettings, icon = Icons.Filled.Settings, primary = true)
    }
}

@Composable
private fun CrashCard(text: String, onClose: () -> Unit) {
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()
            .background(Kino.Surface, RoundedCornerShape(12.dp)).padding(14.dp),
    ) {
        Text("В прошлый раз приложение закрылось с ошибкой", color = Kino.Bad, fontWeight = FontWeight.Bold)
        Text(text.lines().take(14).joinToString("\n"), color = Kino.TextDim, fontSize = 11.sp, maxLines = 14)
        Spacer(Modifier.height(8.dp))
        KButton("Понятно", onClose)
    }
}

// ---------------------------------------------------------------- Поиск

@Composable
private fun SearchScreen(vm: MainVm) {
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KButton("", { vm.back() }, icon = Icons.Filled.ArrowBack)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("Название фильма") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                colors = fieldColors(),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(12.dp))
            KButton("Найти", vm::search, icon = Icons.Filled.Search, primary = true)
        }
        when (val r = vm.results) {
            null -> Hint("Введите название — найдём фильм в каталоге и проверим, где его можно посмотреть бесплатно.")
            Load.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                CircularProgressIndicator(color = Kino.Accent)
            }
            is Load.Fail -> Hint(r.message)
            is Load.Ok -> if (r.value.isEmpty()) {
                Hint("Ничего не нашлось")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(r.value, key = { it.id }) { m ->
                        PosterCard(m, 132.dp, onClick = { vm.open(Screen.Details(m)) })
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (vm.results == null) runCatching { focus.requestFocus() } }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Kino.TextDim, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
}

// ---------------------------------------------------------------- Карточка фильма

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsScreen(vm: MainVm, movie: Movie) {
    val ctx = LocalContext.current
    val playFocus = remember { FocusRequester() }
    fun play(src: Source) {
        vm.onWatch(movie)
        ctx.startActivity(PlayerActivity.intent(ctx, movie, src))
    }

    Box(Modifier.fillMaxSize()) {
        // Фоновый кадр с затемнением
        movie.backdrop?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Kino.Bg, Kino.Bg.copy(alpha = 0.92f), Kino.Bg.copy(alpha = 0.6f))),
                ),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 640.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                KButton("Назад", { vm.back() }, icon = Icons.Filled.ArrowBack)
                Spacer(Modifier.height(16.dp))
                val info: @Composable () -> Unit = {
                    Column {
                        Text(movie.name, color = Kino.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        movie.altName?.takeIf { it != movie.name }?.let {
                            Text(it, color = Kino.TextDim, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        val meta = listOfNotNull(
                            movie.year.takeIf { it > 0 }?.toString(),
                            movie.countries.take(2).joinToString(", ").ifBlank { null },
                            movie.genres.take(3).joinToString(", ").ifBlank { null },
                            movie.lengthMin.takeIf { it > 0 }?.let { formatDuration(it * 60L) },
                        ).joinToString(" · ")
                        Text(meta, color = Kino.TextDim, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            if (movie.ratingKp > 0) Rating("КП", movie.ratingKp)
                            if (movie.ratingImdb > 0) Rating("IMDb", movie.ratingImdb)
                        }
                        movie.description?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Kino.Text, fontSize = 15.sp, maxLines = if (wide) 6 else 10, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (wide) {
                    Row {
                        Poster(movie, Modifier.width(200.dp))
                        Spacer(Modifier.width(24.dp))
                        Box(Modifier.weight(1f)) { info() }
                    }
                } else {
                    Poster(movie, Modifier.width(160.dp))
                    Spacer(Modifier.height(14.dp))
                    info()
                }

                Spacer(Modifier.height(20.dp))
                when (val s = vm.sources) {
                    Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Kino.Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Ищем у правообладателей на RuTube и YouTube…", color = Kino.TextDim)
                    }
                    is Load.Fail -> Text(s.message, color = Kino.Bad)
                    is Load.Ok -> {
                        val list = s.value.sources
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (list.isNotEmpty()) {
                                KButton(
                                    "Смотреть", { play(list.first()) }, icon = Icons.Filled.PlayArrow, primary = true,
                                    modifier = Modifier.focusRequester(playFocus),
                                )
                            }
                            KButton("Кинопоиск", {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movie.kinopoiskUrl))) }
                            }, icon = Icons.Filled.OpenInNew)
                            KButton("Искать заново", { vm.findSources(movie, force = true) }, icon = Icons.Filled.Refresh)
                            if (vm.history.any { it.id == movie.id }) {
                                KButton("Убрать из «Продолжить»", { vm.forget(movie) })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (list.isEmpty()) {
                            Text(
                                "Бесплатной версии от правообладателя не нашлось. " +
                                    "Фильм может быть в онлайн-кинотеатрах — ссылка на Кинопоиск выше.",
                                color = Kino.TextDim, fontSize = 15.sp,
                            )
                        } else {
                            Text("Где посмотреть", color = Kino.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            list.forEach { src -> SourceRow(src) { play(src) } }
                        }
                        s.value.errors.forEach { Text(it, color = Kino.TextDim, fontSize = 12.sp) }
                        LaunchedEffect(list.isNotEmpty()) {
                            if (list.isNotEmpty()) runCatching { playFocus.requestFocus() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(movie: Movie, modifier: Modifier) {
    AsyncImage(
        model = movie.poster, contentDescription = movie.name, contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Kino.SurfaceHi),
    )
}

@Composable
private fun Rating(label: String, value: Double) {
    Text(
        "$label ${String.format(java.util.Locale.US, "%.1f", value)}",
        color = Kino.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.padding(end = 8.dp)
            .background(ratingColor(value), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SourceRow(src: Source, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(vertical = 5.dp).fillMaxWidth().heightIn(min = 64.dp)
            .focusGlow(shape, 1.02f).clip(shape).background(Kino.Surface)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = src.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Kino.SurfaceHi),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(src.title, color = Kino.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val badge = if (src.official) " ✓" else ""
            Text(
                "${src.platform.title} · ${src.channel}$badge · ${formatDuration(src.durationSec)}",
                color = Kino.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (src.platform == Platform.RUTUBE) "RT" else "YT",
            color = Kino.OnAccent, fontWeight = FontWeight.Black, fontSize = 12.sp,
            modifier = Modifier.background(
                if (src.platform == Platform.RUTUBE) Color(0xFF7B61FF) else Color(0xFFE5484D),
                RoundedCornerShape(5.dp),
            ).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------- Настройки

@Composable
private fun SettingsScreen(vm: MainVm) {
    val prefs = vm.prefs
    var key by remember { mutableStateOf(prefs.apiKey) }
    var whitelist by remember { mutableStateOf(prefs.whitelist.joinToString("\n")) }
    var trust by remember { mutableStateOf(prefs.trustVerified) }
    var quality by remember { mutableStateOf(prefs.maxHeight) }
    var saved by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KButton("Назад", { vm.back() }, icon = Icons.Filled.ArrowBack)
            Spacer(Modifier.width(16.dp))
            Text("Настройки", color = Kino.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        SettingTitle("Ключ API Кинопоиска")
        Text("Бесплатный ключ выдаёт Telegram-бот @kinopoiskdev_bot.", color = Kino.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it }, singleLine = true,
            placeholder = { Text("XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX") },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KButton("Сохранить ключ", {
            vm.saveKey(key)
            saved = "Ключ сохранён"
        }, primary = true)

        SettingTitle("Качество YouTube")
        Row {
            listOf(720, 1080).forEach { h ->
                KButton(
                    "${h}p", { quality = h; prefs.maxHeight = h },
                    primary = quality == h, modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
        Text("RuTube подбирает качество сам под скорость сети.", color = Kino.TextDim, fontSize = 13.sp)

        SettingTitle("Каналы правообладателей")
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().focusGlow(shape, 1.01f).clip(shape).background(Kino.Surface)
                .clickable { trust = !trust; prefs.trustVerified = trust; prefs.clearSourceCache() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Также доверять каналам с официальной отметкой площадки (галочка YouTube, «официальный» на RuTube)",
                color = Kino.Text, fontSize = 15.sp, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Switch(checked = trust, onCheckedChange = null)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Белый список: части названий каналов, по одному в строке. Фильмы берутся только с этих каналов.",
            color = Kino.TextDim, fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = whitelist, onValueChange = { whitelist = it },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            KButton("Сохранить список", {
                prefs.whitelist = Prefs.splitList(whitelist)
                prefs.clearSourceCache()
                saved = "Список сохранён"
            }, primary = true)
            Spacer(Modifier.width(10.dp))
            KButton("По умолчанию", {
                whitelist = Prefs.DEFAULT_WHITELIST.joinToString("\n")
                prefs.whitelist = Prefs.DEFAULT_WHITELIST
                prefs.clearSourceCache()
            })
        }
        saved?.let { Text(it, color = Kino.Good, modifier = Modifier.padding(top = 8.dp)) }

        SettingTitle("Обновления")
        Text("Версия ${App.instance.versionName()}", color = Kino.TextDim)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.update != null) {
                KButton("Установить обновление", vm::installUpdate, icon = Icons.Filled.SystemUpdate, primary = true)
            } else {
                KButton("Проверить обновления", { vm.checkUpdate(silent = false) }, icon = Icons.Filled.SystemUpdate)
            }
        }
        vm.updateStatus?.let { Text(it, color = Kino.TextDim, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(
        text, color = Kino.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Kino.Accent,
    unfocusedBorderColor = Kino.SurfaceHi,
    focusedTextColor = Kino.Text,
    unfocusedTextColor = Kino.Text,
    cursorColor = Kino.Accent,
    focusedPlaceholderColor = Kino.TextDim,
    unfocusedPlaceholderColor = Kino.TextDim,
)
