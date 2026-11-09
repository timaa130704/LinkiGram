# LinkiGram — весь функционал и где он лежит

Единый инвентарь функционала LinkiGram для портирования в ПК-клиент
(Telegram Desktop / `tdesktop`, см. [`desktop/README.md`](desktop/README.md)).

Для каждой функции указаны три вещи:

| Колонка | Что значит |
| --- | --- |
| **Название** | видимое имя (обычно строка из `values/strings.xml`); в скобках — ключ ресурса |
| **Тип** | экран, настройка, контроллер/сервис, UI-компонент, интеграция/хук, ресурс/тема |
| **Где** | текущий путь в Android-коде и предлагаемый модуль в `tdesktop` |

Общие соглашения:

- Текущие пути даны от `TMessagesProj/src/main/java/`; `org/telegram` — база
  Telegram, `app/nimarkogram` — код LinkiGram, `app/exteraless` — вендоренный
  код exteraless (ИИ-ассистент, плеер, лирика, Last.fm, Vosk).
- Целевые пути ПК-клиента — предлагаемые модули под закреплённой ревизией
  `tdesktop`, вида `Telegram/SourceFiles/linkigram/…`. Точные имена файлов
  следуют ревизии из `desktop-windows.yml`. Переиспользование штатных папок
  `tdesktop` (`settings/`, `window/`, `media/`, `boxes/`, `calls/`, `core/`)
  ожидается.
- **Плагинные API Android не входят в контракт ПК-клиента.** Плагины и всё, что
  связано с DEX/Python/Chaquopy, вынесены в раздел «Только Android» и не
  портируются.
- **Исключено из этого документа:** ИИ-агент плагинов
  (`app/nimarkogram/messenger/plugins/ui/PluginAgentActivity.java`,
  `PluginAgentPrompt.java`) — Android-only помощник плагинов.

## Точки интеграции с базой Telegram

Только несколько файлов upstream импортируют код LinkiGram — это хуки, которые
ПК-порт должен воспроизвести нативно:

| Файл upstream | Что подключает |
| --- | --- |
| `org/telegram/ui/ActionBar/DrawerLayoutContainer.java` | Боковое меню (drawer) |
| `org/telegram/ui/DialogsActivity.java` | Список чатов, папки, нижние табы, «Прочитать все» |
| `org/telegram/ui/ChatActivity.java` | Меню чата, меню сообщения, ghost-хуки |
| `org/telegram/ui/Cells/ChatMessageCell.java` | Пузыри, градиенты, удалённые сообщения |
| `org/telegram/ui/ProfileActivity.java` | Профильные дополнения, музыкальный штамп, бейджи |
| `org/telegram/ui/Components/ChatActivityEnterView.java` | Композер (iOS-стиль, перевод перед отправкой) |
| `org/telegram/ui/Components/InstantCameraView.java`, `InstantCameraView2.java` | Круглые видеосообщения (CameraX) |
| `org/telegram/ui/Components/UniversalFragment.java`, `UniversalRecyclerView.java` | Карточки/стекло настроек LinkiGram |
| `org/telegram/ui/Components/chat/layouts/ChatActivityActionsButtonsLayout.java` | Кнопки действий в чате |
| `org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java` | Провайдер размытия/стекла |

---

# 1. Брендинг, тема и внешний вид

## Брендинг

| Название | Тип | Где (Android) | Куда (ПК, предлагается) |
| --- | --- | --- | --- |
| LinkiGram Settings (хаб) | Экран | `app/nimarkogram/messenger/preferences/MainPreferencesActivity.java` | `linkigram/settings/main.cpp` |
| About-заголовок | UI-компонент | `MainPreferencesActivity.createAboutHeader()`, `res/drawable/linkigram_about_icon` | `linkigram/settings/about.cpp` |
| Имя/пакет/версия приложения | Ресурс/конфиг | `gradle.properties` (`APP_VERSION_NAME`, `APP_VERSION_CODE`, `APP_PACKAGE`) | `desktop-windows.yml`, CMake-брендинг `tdesktop` |
| Пункт «Исходный код» | Настройка/ссылка | `MainPreferencesActivity` (`SOURCE_REPOSITORY_URL`) | `linkigram/settings/about.cpp` |
| Поиск по настройкам | Сервис | `app/nimarkogram/messenger/search/LinkiSettingsSearch.java`, `LinkiSettingsCatalog.java`, `LinkiSettingsEnglishCatalog.java` | `linkigram/settings/search.cpp` |

## Внешний вид — стили, стекло, You

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Экран «Внешний вид» | Экран | `preferences/AppearancePreferencesActivity.java` | `linkigram/settings/appearance.cpp` |
| LinkiGram You (тема) | Тема | `appearance/LinkiGramYouTheme.java` | `linkigram/theme/you.cpp` |
| ColorOS 17 | Тема | `appearance/ColorOs17Theme.java` | `linkigram/theme/coloros17.cpp` |
| Material Design / MD3 | Тема | `appearance/MaterialStyle.java`, `MaterialProgress.java` | `linkigram/theme/material.cpp` |
| Движок темы | Сервис | `appearance/ClientTheme.java`, `AppearanceConfig.java`, `CustomizationPreferences.java` | `linkigram/theme/theme.cpp` |
| Linki Glass | Настройка+сервис | `appearance/ClientTheme.glassEnabled()`, `ui/LinkiGlassDrawable.java`, `org/telegram/ui/Components/blur3/…` | `linkigram/theme/glass.cpp` |
| Force blur | Настройка | `AppearancePreferencesActivity` (`NM_ForceBlur`) | `linkigram/theme/glass.cpp` |
| Цвета иконок настроек | Настройка+UI | `appearance/SettingsIconColors.java` | `linkigram/theme/settings_icons.cpp` |
| Набор иконок | Настройка+сервис | `icons/CustomSvgIconPack.java`, `IconReplaceUtils.java`, `NimarkoIconResources.java`, `preferences/IconPackSelectorActivity.java` | `linkigram/theme/icon_pack.cpp` |
| Стиль переключателей | Настройка | `AppearancePreferencesActivity` (`NM_SwitchStyle`) | `linkigram/theme/switches.cpp` |
| Шапка: центр. заголовок, поиск | Настройки | `AppearancePreferencesActivity` (`AP_Header`, `AP_CenterTitle`, `AP_HideSearchBar`, `NM_HideActionBarStatus`) | `linkigram/window/header.cpp` |
| Снежинки | Настройка+UI | `AppearancePreferencesActivity` (`CP_Snowflakes_Header`) | `linkigram/effects/snowflakes.cpp` |
| Кастомный заголовок | Настройка | `AppearancePreferencesActivity` (`NM_CustomTitle`) | `linkigram/window/header.cpp` |
| Классический интерфейс | Настройка | `AppearancePreferencesActivity` (`NM_ClassicUi_Header`, `NM_ClassicUi`) | `linkigram/theme/classic.cpp` |
| Вид чата (iOS-композер, хвост, индикатор) | Настройки | `AppearancePreferencesActivity` (`NM_Chat_Section`, `NM_IOSStyleComposer`, `NM_HideBubbleTail`, `NM_OnlineIndicatorInGroups`, `NM_DisableSendHints`) | `linkigram/chat/look.cpp`, `linkigram/chat/composer.cpp` |
| Эффекты: блики, свечение медиа | Настройки+UI | `AppearancePreferencesActivity` (`AP_GlareOnElements`, `NM_MediaGlow`), `mediaglow/MediaGlowController.java` | `linkigram/effects/glow.cpp` |
| Аватары / единое скругление | Настройки | `AppearancePreferencesActivity` (`NM_AvatarCorners_Header`, `NM_ForumAvatarsLikeChats`) | `linkigram/ui/avatars.cpp` |
| Опции стикеров | Настройка | `AppearancePreferencesActivity` (`CP_TimeOnStick`) | `linkigram/chat/stickers.cpp` |
| MD3-виджеты (лоадеры/слайдеры/…) | Настройки+компоненты | `AppearancePreferencesActivity` (`NM_Style_*`) | `linkigram/theme/md3.cpp` |
| Вид плеера / мини-плеера | Настройки | `AppearancePreferencesActivity` (`OEAppearanceMd3Player`, `OEAppearanceMd3MiniPlayer`) | `linkigram/media/player_style.cpp` |

## Боковое меню (drawer)

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Настройки бокового меню | Экран | `preferences/DrawerPreferencesActivity.java` | `linkigram/window/drawer_settings.cpp` |
| Внешний вид бокового меню | Экран | `preferences/ScreenAppearanceActivity.java` | `linkigram/window/drawer_settings.cpp` |
| UI drawer | Компоненты | `drawer/` (`DrawerContainer`, `DrawerMenuView`, `DrawerMenuItemView`, `DrawerHeaderView`, `MainMenuLayout`, `MainMenuHelper`, `MainMenuItem`, `DrawerAccountPickerView`, `DrawerUnreadBadge`, `DrawerAccountUnreadBadge`, `DrawerUiUtil`) | `linkigram/window/drawer.cpp` |
| Иммерсивная анимация меню | Настройка | `AppearancePreferencesActivity` (`NM_Drawer_Immersive`) | `linkigram/window/drawer.cpp` |
| Аккаунты в меню | Настройка | `DebugPreferencesActivity` (`NM_DBG_ShowAccounts`) | `linkigram/window/drawer.cpp` |

## Фон и анимация текста

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Фон приложения | Экран | `preferences/BackgroundPreferencesActivity.java` | `linkigram/theme/background.cpp` |
| Анимация текста | Экран+сервис | `preferences/NimarkoTextAnimPreferencesActivity.java`, `textanim/NimarkoTextAnim.java` | `linkigram/effects/text_animation.cpp` |
| Градиент пузырей | Настройки | `GeneralPreferencesActivity` (`NM_InBubbleGradients`, `NM_InBubbleGradient1..3`) | `linkigram/chat/bubbles.cpp` |

## Переиспользуемые компоненты

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| MD3-адаптеры списков | Компоненты | `ui/MD3ListAdapter.java`, `MD3AdapterWithDiffUtils.java` | `linkigram/ui/md3_list.cpp` |
| JSON bottom sheet | Компонент | `ui/NimarkoJsonBottomSheet.java` | `linkigram/ui/json_sheet.cpp` |

---

# 2. Чаты, папки, профили и меню сообщений

## Список чатов

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Экран «Чаты» | Экран | `preferences/ChatsPreferencesActivity.java` | `linkigram/settings/chats.cpp` |
| Экран «Сообщения» | Экран | `preferences/MessagesPreferencesActivity.java` | `linkigram/settings/messages.cpp` |
| Список диалогов | Настройки | `ChatsPreferencesActivity` (`CP_Header_DialogList`, `CP_SortByUnread`, `CP_UnarchiveOnSwipe`, `CP_OpenSettingsBySwipe`, `ForwardWithoutAuthor`, `EP_CustomChat`, `CP_Slider_RecentEmojisAndStickers`) | `linkigram/chats/list.cpp` |
| «Прочитать все в меню» | Настройка+интеграция | `NimarkoConfig.readAllInMenu`, `ChatsPreferencesActivity` (`NM_CP_ReadAllInMenu`), `org/telegram/ui/DialogsActivity.java` | `linkigram/chats/read_all.cpp` |
| Вид чата | Настройки | `ChatsPreferencesActivity` (`NM_CP_Header_ChatView`, `NM_CP_CenterTitleInChat`, `CP_UnreadBadgeOnBackButton`, `CP_CustomWallpapers`, `NM_CP_SnowflakesInChat`, `AP_ShowPencilIcon`, `CP_ForwardMsgDate`, `CP_HideMuteUnmuteButton`, `NM_CP_WeekdayNearDate`, `CP_HideSendAsChannel`, `CP_Messages_Size`, `CP_ChatMenuShortcuts`) | `linkigram/chats/dialog.cpp` |
| Взаимодействие | Настройки | `ChatsPreferencesActivity` (`NM_CP_Header_Interaction`, `CP_DoubleTapAction`, `NM_MsgSlideAction`, `CP_LeftBottomButtonAction`, `CP_AutoQuoteReplies`, `NM_CP_PreReformRussian`, `NM_CP_LatexRendering`, `CP_DisableSwipeToNext`, `CP_DeleteForAll`, `CP_DisableVibration`, `CP_HideKbdOnScroll`) | `linkigram/chats/interaction.cpp` |
| Недавние эмодзи/стикеры | Экран | `preferences/RecentEmojisStickersPreferencesActivity.java` | `linkigram/chats/recent.cpp` |
| Локальные закрепления | Настройка+сервис | `ChatsPreferencesActivity` (`LC_Pins`), `local/LocalChatPins.java` | `linkigram/chats/local_pins.cpp` |
| Дореформенная орфография | Сервис | `utils/PreReformRussian.java` | `linkigram/text/pre_reform.cpp` |
| LaTeX-формулы | Сервис | `utils/NimarkoLatexHelper.java` | `linkigram/text/latex.cpp` |
| Инъекция в меню чата | Интеграция | `chats/CGChatMenuInjector.java`, `org/telegram/ui/ChatActivity.java` | `linkigram/chats/menu.cpp` |

## Папки и нижние табы

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Папки | Экран | `preferences/FoldersPreferencesActivity.java` | `linkigram/chats/folders.cpp` |
| Органайзер нижних табов | Экран+UI | `preferences/BottomTabsPreferencesActivity.java`, `preferences/tabs/MainTabsPreviewCell.java` | `linkigram/chats/bottom_tabs.cpp` |
| Отрисовка папок/табов | Интеграция | `org/telegram/ui/DialogsActivity.java`, `org/telegram/ui/Components/UniversalRecyclerView.java` | `linkigram/chats/bottom_tabs.cpp` |

## Меню сообщения

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Экран «Меню сообщения» | Экран | `preferences/MessageMenuPreferencesActivity.java` | `linkigram/chat/message_menu.cpp` |
| Пункты меню сообщения | Экран | `preferences/MessageMenuItemsPreferencesActivity.java` | `linkigram/chat/message_menu.cpp` |
| Порядок пунктов меню | Экран | `preferences/MessageMenuOrderPreferencesActivity.java` | `linkigram/chat/message_menu.cpp` |
| Фильтрация сообщений | Экран+сервис | `preferences/MessageFiltersPreferencesActivity.java` | `linkigram/chat/message_filters.cpp` |
| Внешний вид меню | Компоненты | `ui/MessageMenuCompactView.java`, `MessageMenuTelegramPlus.java`, `MessageMenuTweaks.java` | `linkigram/chat/message_menu.cpp` |
| Инструменты сообщений | Сервис | `features/MessageTools.java`, `ChatExtras.java`, `DeletedReplyQuote.java` | `linkigram/chat/tools.cpp` |

## Профили и дополнения

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| «Сообщения и профили» | Экран | `preferences/MessagesAndProfilesPreferencesActivity.java` | `linkigram/profile/extras.cpp` |
| Помощники профиля | Сервисы | `utils/NimarkoProfileActivityHelper.java`, `NimarkoRegDate.java`, `UserHelper.java`, `NimarkoPhotoViewerHelper.java` | `linkigram/profile/extras.cpp` |
| Удалённые Star-подарки | Сервис+настройка | `gifts/NimarkoDeletedGiftsManager.java`, `GeneralPreferencesActivity` (`NM_GEN_DeletedGifts`) | `linkigram/profile/gifts.cpp` |
| Local Premium | Сервис+настройка | `local/LocalPremium.java`, `GeneralPreferencesActivity` (`LC_Premium`, `NM_GEN_LocalPremiumEmoji`) | `linkigram/profile/local_premium.cpp` |
| Локальные настройки аккаунта | Сервис | `local/LocalAccountSettings.java` | `linkigram/profile/local_account.cpp` |
| Трекер последнего онлайна | Сервис+настройки | `utils/LastSeenTracker.java`, `AyuMomentsPreferencesActivity` (`NM_Ayu_SaveLastSeen`, `NM_LastSeenCache`) | `linkigram/profile/last_seen.cpp` |
| Публичный API профиля/бейджей | Сервис | `api/dto/BadgeDTO.java`, `ProfileDTO.java`, `api/model/ProfileStatus.java`, `api/network/ApiClient.java` | `linkigram/api/client.cpp` |

## Бейджи, инфокарты, баннеры, автоматизация

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Бейджи | Сервис+UI | `badges/` (`BadgesController`, `BadgeEntry`, `BadgeUi`, `ApiBadgeSource`) | `linkigram/profile/badges.cpp` |
| Быстрая информация (инфокарты) | Экран+сервис | `infocards/preferences/InfoCardsPreferencesActivity.java`, `infocards/` (`BaseInfoCard`, `CacheCard`, `CryptoCard`, `ProxyCard`, `WeatherCard`, `InfoCardRates`, `InfoCardRegistry`, `InfoCardType`, `InfoCardStripView`, `InfoCardsConfig`) | `linkigram/infocards/` |
| Лента инфокарт | UI-компонент | `infocards/InfoCardStripView.java` | `linkigram/infocards/strip.cpp` |
| Баннеры | Экран+сервис | `preferences/BannerPreferencesActivity.java`, `banners/` (`NimarkoBannerConfig`, `NimarkoBannerController`, `NimarkoBannerHttp`, `NimarkoBannerRenderer`, `BannerCropActivity`) | `linkigram/profile/banners/` |
| ИИ-ассистент чата | Сервис+экран | `automation/ChatAssistant.java`, `preferences/ChatAssistantPreferencesActivity.java` | `linkigram/chat/assistant.cpp` |
| Сводка чата | Сервис | `automation/ChatSummary.java` | `linkigram/chat/summary.cpp` |
| История правок | Сервис+настройки | `automation/EditHistory.java`, `AyuMomentsPreferencesActivity` (`NM_Ayu_SaveEdits`) | `linkigram/chat/edit_history.cpp` |
| Повторяющиеся сообщения | Сервис+пикер | `automation/RepeatingMessages.java`, `RepeatPicker.java` | `linkigram/chat/repeating.cpp` |
| Доп. настройки чата | Экран | `preferences/ChatExtrasPreferencesActivity.java` | `linkigram/settings/chat_extras.cpp` |
| Smart chat center | Экран | `preferences/SmartChatCenterActivity.java` | `linkigram/chat/smart_center.cpp` |
| Автоочистка | Сервис | `utils/NimarkoAutoClean.java` | `linkigram/chats/auto_clean.cpp` |

## Композер и общие компоненты

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| ReceiveContentEditText | Компонент | `components/ReceiveContentEditText.java` | `linkigram/chat/composer.cpp` |
| Выбор субтитров | Компонент | `components/ChooseSubtitlesLayout.java` | `linkigram/media/subtitles.cpp` |
| VerticalImageSpan / ActionRow | Компоненты | `components/VerticalImageSpan.java`, `ActionRow.java` | `linkigram/ui/components.cpp` |
| Селектор иконок | Компонент | `components/IconSelectorAlert.java` | `linkigram/ui/icon_selector.cpp` |
| QR-логин | Компонент | `components/QrCodeLoginView.java`, `utils/QrHelper.java`, `NimarkoInlineAuth.java` | `linkigram/intro/qr_login.cpp` |
| Поиск по фото (попап) | Компонент | `components/SearchPhotoPopupWrapper.java` | `linkigram/media/search_popup.cpp` |
| Перевод перед отправкой | Компонент | `components/TranslateBeforeSendWrapper.java` | `linkigram/translation/before_send.cpp` |
| Общие утилиты | Сервисы | `utils/` (`AppUtils`, `AppRestartHelper`, `CGCompat`, `NimarkoExtra`, `NimarkoDeeplinkHelper`, `NimarkoLocalEmoji`, `LinkTrackingCleaner`, `PermissionsUtils`, `VibrateUtils`, `ResourcesUtils`) | `linkigram/core/utils`, `linkigram/core/deeplinks` |

---

# 3. Медиа, истории и буфер обмена

## Камера (CameraX)

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Экран «Камера» | Экран | `preferences/CameraPreferencesActivity.java` | `linkigram/media/camera_settings.cpp` |
| Контроллер CameraX | Сервис | `camera/NimarkoCameraXController.java`, `CameraXProviderCoordinator.java`, `CameraXZoomCoordinator.java`, `CameraXUtils.java` | `linkigram/media/camera.cpp` |
| Вьюхи камеры | Компоненты | `camera/NimarkoCameraXView.java`, `NimarkoCameraXSurfaceSession.java`, `BaseCameraView.java`, `ButtonEffect.java`, `LockAnimationView.java`, `SlideControlView.java` | `linkigram/media/camera_widgets.cpp` |
| Селекторы линз/типа/эффектов | Компоненты | `camera/CameraLensSelector.java`, `CameraTypeSelector.java`, `EffectSelectorView.java` | `linkigram/media/camera_selectors.cpp` |
| Круглые видеосообщения | Сервис+хуки | `camera/NimarkoVideoMessagesHelper.java`, `JpegImageUtils.java`, `org/telegram/ui/Components/InstantCameraView.java`, `InstantCameraView2.java` | `linkigram/media/round_video.cpp` |

## Медиа-загрузчик и Once

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Media Downloader | Экран+сервис | `preferences/NimarkoMediaPreferencesActivity.java`, `media/NimarkoMediaDownloader.java` | `linkigram/media/downloader/` |
| Контроллер медиа | Сервис | `media/NimarkoMediaController.java` | `linkigram/media/controller.cpp` |
| Once-медиа (архив) | Экран+сервис | `preferences/OnceMediaArchiveActivity.java`, `media/OnceMediaArchive.java` | `linkigram/media/once.cpp` |
| Улучшение звука | Сервис | `utils/NimarkoAudioEnhance.java` | `linkigram/media/audio_enhance.cpp` |

## Плеер, лирика, плейлисты и Last.fm (пакет exteraless)

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| MD3-плеер | Экран/контроллер | `app/exteraless/player/Md3Player.java`, `PlayerSheet.java` | `linkigram/media/player/player.cpp` |
| UI плеера | Компоненты | `app/exteraless/player/PlayerSheet.java`, `PlayerArt.java`, `PlayerColors.java`, `ControlsRow.java`, `RingPlayButton.java`, `MorphButton.java`, `Spring.java`, `WavySeekBar.java`, `PlayerIcon.java`, `PlayerBarView.java`, `PlayerMiniView.java` | `linkigram/media/player/ui.cpp` |
| Очередь воспроизведения | Компонент | `app/exteraless/player/PlayerQueueSheet.java`, `PlayerActions.java` | `linkigram/media/player/queue.cpp` |
| Плейлисты | UI+сервис | `app/exteraless/player/PlayerPlaylists.java`, `PlayerPlaylistsSheet.java` | `linkigram/media/player/playlists.cpp` |
| Аудио-закладки | Сервис | `app/exteraless/player/AudioBookmarks.java` | `linkigram/media/player/bookmarks.cpp` |
| Обложка/арт | Компонент | `app/exteraless/player/CoverImage.java` | `linkigram/media/player/cover.cpp` |
| Лирика | UI+сервис | `app/exteraless/player/LyricsView.java`, `Lyrics.java`, `LyricsWorkers.java`, `LyricsDeadline.java`, `OnlineLyrics.java`, `WordLyricsParser.java` | `linkigram/media/player/lyrics.cpp` |
| Last.fm (сейчас играет) | Сервис+настройки | `app/exteraless/nowplaying/NowPlayingController.java`, `LastFmNowPlaying.java`, `LastFmWebFetcher.java`, `GeneralPreferencesActivity` (`OEGeneralLastFm`) | `linkigram/media/lastfm.cpp` |
| Музыкальная метка профиля | Компоненты | `app/exteraless/nowplaying/ProfileMusicMark.java`, `ProfileMusicStamp.java`, `app/exteraless/components/ProfileMusicCard.java` | `linkigram/profile/music.cpp` |

## Речь (Vosk) и транскрипция

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Настройки Vosk | Экран | `app/exteraless/speech/VoskSettingsActivity.java` | `linkigram/speech/settings.cpp` |
| Vosk-менеджер/транскрипция | Сервис | `app/exteraless/speech/VoskManager.java`, `VoskTranscriber.java`, `VoskModel.java`, `VoskModels.java` | `linkigram/speech/vosk.cpp` |
| Индикатор загрузки модели | Компонент | `app/nimarkogram/messenger/speech/ui/LoadingModelView.java` | `linkigram/speech/ui.cpp` |

## Истории и буфер обмена

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Опции историй | Настройки | `GeneralPreferencesActivity` (`FilterStories`, `CP_HideStories`, `CP_ArchiveStories`) | `linkigram/stories/settings.cpp` |
| Приватность архивных историй | Настройка | `PrivacyPreferencesActivity` (`NM_PR_HideArchivedStories`) | `linkigram/stories/privacy.cpp` |
| Буфер обмена (изображения) | Сервис | `utils/ImageClipboardUtils.java` | `linkigram/clipboard.cpp` |
| Direct Share | Настройка | `ChatsPreferencesActivity`, `MessagesPreferencesActivity` (`DirectShare`) | `linkigram/chats/direct_share.cpp` |
| Просмотрщик фото | Сервис | `utils/NimarkoPhotoViewerHelper.java` | `linkigram/media/viewer.cpp` |

---

# 4. Приватность, перевод, сеть и обновления

## Ghost / AyuMoments и приватность

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| AyuMoments (ghost) | Экран | `preferences/AyuMomentsPreferencesActivity.java` | `linkigram/privacy/ghost.cpp` |
| Режим ghost (мастер) | Сервис | `features/` + ghost-настройки `NimarkoConfig` | `linkigram/privacy/ghost.cpp` |
| Сохранение удалённых сообщений | Сервис+настройки | `features/`, `AyuMomentsPreferencesActivity` (`NM_SaveDeletedMessages`, …) | `linkigram/privacy/deleted.cpp` |
| История правок | Сервис+настройки | `automation/EditHistory.java` | `linkigram/privacy/edits.cpp` |
| Сохранение медиа | Сервис+настройки | `AyuMomentsPreferencesActivity` (`NM_Ayu_SaveMedia…`) | `linkigram/privacy/media.cpp` |
| Локальный трекинг | Сервис+настройки | `utils/LastSeenTracker.java` | `linkigram/privacy/tracking.cpp` |
| База Ayu | Экран | `AyuMomentsPreferencesActivity` (`NM_Ayu_DbExport`, `NM_Ayu_DbImport`, `NM_Ayu_DbClear`) | `linkigram/privacy/db.cpp` |
| Приватность (экран) | Экран | `preferences/PrivacyPreferencesActivity.java` | `linkigram/settings/privacy.cpp` |
| Быстрые режимы приватности | Экран | `preferences/QuickPrivacyModesActivity.java` | `linkigram/privacy/modes.cpp` |
| Заблокированные чаты / биометрия | Экран+сервис | `preferences/LockedChatsPreferencesActivity.java`, `security/NimarkoBiometricPrompt.java`, `utils/LockedChats.java`, `utils/BiometricPrompt.java` | `linkigram/privacy/locked.cpp` |

## Перевод и язык

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Переводчик (база) | Сервис | `utils/text/BaseTranslator.java`, `Translator.java`, `TelegramTranslator.java`, `TranslatorUtils.java` | `linkigram/translation/` |
| Локальный ML Kit перевод | Сервис | `com.google.mlkit:translate` (зависимость), перевод в шите сообщения | `linkigram/translation/offline.cpp` |
| Локализация/локаль | Сервис | `utils/text/LocaleUtils.java`, `utils/text/` | `linkigram/core/locale.cpp` |
| Pre-reform русский | Сервис | `utils/PreReformRussian.java` | `linkigram/text/pre_reform.cpp` |

## Сеть и обход блокировок

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Bypass (WS) | Экран+сервис | `wsbypass/preferences/WsBypassPreferencesActivity.java`, `wsbypass/` (`NimarkoWsBypassController`, `WsBypassCore`, `RawWebSocket`, `WebSocketPool`, `DomainPool`, `ProxyApplier`, `MsgSplitter`, `MtprotoHandshake`, `TgWsBridge`, `CryptoCtx`, `WlAccess`, `WlRetryGate`, `WsRelayAuth`, `RelayRegion`, `NimarkoVpnDetector`) | `linkigram/net/wsbypass/` |
| X-CFG (встроенный VPN) | Сервис | `xcfg/` (`XcfgCore`, `XcfgController`, `XcfgApi`, `XcfgCabinet`, `XcfgVless`, `XcfgPrefs`) | `linkigram/net/xcfg/` |
| Диагностика соединения | Экран | `preferences/LinkiDiagnosticsActivity.java` | `linkigram/net/diagnostics.cpp` |
| Настройки сети | Настройки | `GeneralPreferencesActivity` (`EP_Network`, `EP_DownloadSpeedBoost`, `NM_GE_UploadSpeedBoost`, `EP_SlowNetworkMode`) | `linkigram/net/settings.cpp` |

## Обновления, бэкап, диагностика

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Обновление приложения | Сервис+шит | `updater/NimarkoUpdater.java`, `LinkiUpdateSheet.java`, `NimarkoUpdateConfig.java`, `NimarkoUpdaterReceiver.java`, `MainPreferencesActivity` (`UP_CheckForUpdates`) | `linkigram/updater/` |
| Экспорт/импорт настроек | Сервис+настройки | `utils/NimarkoConfigBackup.java`, `GeneralPreferencesActivity` (`NM_Config_Export`, `NM_Config_Import`) | `linkigram/core/settings_backup.cpp` |
| Debug-настройки | Экран | `preferences/DebugPreferencesActivity.java` | `linkigram/settings/debug.cpp` |
| Экспериментальные | Экран | `preferences/ExperimentalPreferencesActivity.java` | `linkigram/settings/experimental.cpp` |

---

# 5. Звонки, платформа и перенос сессий

| Название | Тип | Где (Android) | Куда (ПК) |
| --- | --- | --- | --- |
| Звонки через bypass (VoIP) | Настройка+сервис | `wsbypass` (`NM_WSB_VoIP_Enable`), `TgWsBridge` | `linkigram/calls/routing.cpp` |
| Проверка VPN / приостановка | Сервис+настройка | `wsbypass/NimarkoVpnDetector.java`, `NM_WSB_SuspendOnVpn` | `linkigram/net/vpn_detect.cpp` |
| Перенос сессии (импорт/экспорт) | Экран+сервис | `org/telegram/messenger/sessions/` (`SessionData`, `SessionFiles`, `SessionTransfer`, `StringSessionCodec`, `TdataCodec`) | `linkigram/intro/session_transfer.cpp` |
| QR-логин | Компонент | `components/QrCodeLoginView.java`, `utils/QrHelper.java`, `NimarkoInlineAuth.java` | `linkigram/intro/qr_login.cpp` |
| Платформенные утилиты | Сервис | `utils/PermissionsUtils.java`, `VibrateUtils.java`, `AppUtils.java`, `AppRestartHelper.java`, `CGCompat.java` | `linkigram/core/platform.cpp` |
| Плагинная подсистема UI-хуков | Интеграция | `plugins/`, `plugins/ui/` (кроме ИИ-агента) | **только Android, не портируется** |

---

# 6. Справка по экранам настроек

Все настройки LinkiGram: 29 экранов, 411 записей. Формат — `Название (R.string.Ключ)`.

Всего: **29 экранов**, **422 записей**.

## MainPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MainPreferencesActivity.java` · ПК-модуль: `linkigram/settings/main.cpp`

- LinkiGram Settings (`R.string.NimarkoGramSettings`)

## GeneralPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/GeneralPreferencesActivity.java` · ПК-модуль: `linkigram/settings/general.cpp`

- General (`R.string.AP_Header_General`)
- AI Assistant (`R.string.OEAiTitle`)
- Relative last seen (`R.string.LinkiRelativeLastSeen`)
- Hide my phone number locally (`R.string.LinkiHidePhone`)
- Animations (`R.string.LiteMode`)
- Navigation animation (`R.string.EP_NavigationAnimation`)
- Crossfading effect (`R.string.EP_NavigationAnimationCrossfading`)
- Predictive-Back navigation (`R.string.NM_PredictiveBackAnimation`)
- Notifications (`R.string.SettingsNotifications`)
- Silence non-contacts (`R.string.CP_SilenceNonContacts`)
- Show a resident notification (`R.string.NM_ResidentNotification`)
- Reactions in notifications (`R.string.NM_NotificationReactions`)
- Stories (`R.string.FilterStories`)
- Hide Stories (locally) (`R.string.CP_HideStories`)
- Archive stories (`R.string.CP_ArchiveStories`)
- Miscellaneous (`R.string.LocalMiscellaneousCache`)
- System emoji (`R.string.AP_SystemEmoji`)
- System fonts (`R.string.AP_SystemFonts`)
- Tablet mode (`R.string.AP_Tablet_Mode`)
- Last.fm (`R.string.OEGeneralLastFm`)
- X-CFG tab in the bottom bar (`R.string.XcfgTabToggle`)
- Network (`R.string.EP_Network`)
- Download speed boost (`R.string.EP_DownloadSpeedBoost`)
- Upload speed boost (`R.string.NM_GE_UploadSpeedBoost`)
- Slow network mode (`R.string.EP_SlowNetworkMode`)
- Miscellaneous (`R.string.NM_GEN_MiscHeader`)
- Show deleted Star gifts (`R.string.NM_GEN_DeletedGifts`)
- Local Premium (`R.string.LC_Premium`)
- Local premium emoji (`R.string.NM_GEN_LocalPremiumEmoji`)
- Block ads (`R.string.NM_GEN_BlockAds`)
- Sleep timer (`R.string.CG_Sleep`)
- Other (`R.string.NM_GEN_OtherHeader`)
- Hide incoming typing (`R.string.NM_HideIncomingTyping`)
- Gradients for incoming bubbles (`R.string.NM_InBubbleGradients`)
- Gradient color 1 (`R.string.NM_InBubbleGradient1`)
- Gradient color 2 (`R.string.NM_InBubbleGradient2`)
- Gradient color 3 (`R.string.NM_InBubbleGradient3`)
- Send photos without compression (`R.string.NM_SendOriginalPhoto`)
- Configuration (`R.string.NM_Config_Header`)
- Export settings (`R.string.NM_Config_Export`)
- Import settings (`R.string.NM_Config_Import`)

## AppearancePreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/AppearancePreferencesActivity.java` · ПК-модуль: `linkigram/settings/appearance.cpp`

- Appearance (`R.string.AP_Header_Appearance`)
- LinkiGram You (`R.string.NM_LinkiGramYou`)
- ColorOS 17 (`R.string.NM_ColorOS17`)
- LinkiGram You customization (`R.string.LinkiYouCustomize`)
- Side menu settings (`R.string.LF_Menu`)
- Icon pack (`R.string.AP_IconReplacements`)
- Settings icon colors (`R.string.LinkiSettingsIconColors`)
- Switch style (`R.string.NM_SwitchStyle`)
- Material Design (`R.string.NM_Style_Material`)
- Enable all Material 3 styles (`R.string.NM_Material_All`)
- iOS Design (`R.string.NM_Style_IOS`)
- Section dividers (`R.string.NM_Material_Dividers`)
- Separate section headers (`R.string.NM_Material_Headers`)
- Material 3 cards (`R.string.AP_MaterialCards`)
- Card corner radius (`R.string.AP_SectionRadius`)
- Force blur (`R.string.NM_ForceBlur`)
- Linki Glass (`R.string.NM_LiquidGlass`)
- Header (`R.string.AP_Header`)
- Center title (`R.string.AP_CenterTitle`)
- Hide search bar in chats (`R.string.AP_HideSearchBar`)
- Hide status in chat header (`R.string.NM_HideActionBarStatus`)
- Snowflakes (`R.string.CP_Snowflakes_Header`)
- Custom title (`R.string.NM_CustomTitle`)
- Interface style (`R.string.NM_ClassicUi_Header`)
- Classic interface (`R.string.NM_ClassicUi`)
- Side menu (drawer) (`R.string.NM_Drawer_NavigationDrawer`)
- Immersive drawer animation (`R.string.NM_Drawer_Immersive`)
- Chats (`R.string.NM_Chat_Section`)
- iOS-style message input (`R.string.NM_IOSStyleComposer`)
- Hide bubble tail (`R.string.NM_HideBubbleTail`)
- Online indicator in groups (`R.string.NM_OnlineIndicatorInGroups`)
- Disable send hints (`R.string.NM_DisableSendHints`)
- Effects (`R.string.NM_Effects_Header`)
- Glare on elements (`R.string.AP_GlareOnElements`)
- Media glow (`R.string.NM_MediaGlow`)
- Avatars (`R.string.NM_AvatarCorners_Header`)
- Unified rounding (`R.string.NM_ForumAvatarsLikeChats`)
- Stickers (`R.string.NM_Stickers_Header`)
- Sticker timestamp (`R.string.LinkiStickerTime`)
- Miscellaneous (`R.string.LocalMiscellaneousCache`)
- Folders (`R.string.CP_Filters_Header`)
- Bottom tabs organizer (`R.string.CP_MainTabs_Header`)
- App background (`R.string.NM_BG_Title`)
- Messages and profiles (`R.string.CP_ProfileReplyBackground`)
- MD3 loading indicators (`R.string.NM_Style_Loaders`)
- MD3 sliders (`R.string.NM_Style_Sliders`)
- MD3 switches (`R.string.NM_Style_Switches`)
- Chat header (`R.string.NM_Style_ChatHeader`)
- Navigation bar (`R.string.NM_Style_Navigation`)
- List items (`R.string.NM_Style_ListItems`)
- Music player (`R.string.OEAppearanceMd3Player`)
- Mini player (`R.string.OEAppearanceMd3MiniPlayer`)

## ChatsPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/ChatsPreferencesActivity.java` · ПК-модуль: `linkigram/settings/chats.cpp`

- Chats (`R.string.FilterChats`)
- Chat tools (`R.string.LX_Title`)
- Dialog list (`R.string.CP_Header_DialogList`)
- Sort by unread (`R.string.CP_SortByUnread`)
- Unarchive on swipe (`R.string.CP_UnarchiveOnSwipe`)
- Open settings on swipe down (`R.string.CP_OpenSettingsBySwipe`)
- Read all in the menu (`R.string.NM_CP_ReadAllInMenu`)
- Forward without author (`R.string.ForwardWithoutAuthor`)
- Custom chat for Saved Messages (`R.string.EP_CustomChat`)
- Recent emoji/sticker count (`R.string.CP_Slider_RecentEmojisAndStickers`)
- Chat view (`R.string.NM_CP_Header_ChatView`)
- Center title in chat (`R.string.NM_CP_CenterTitleInChat`)
- Show unread chats counter (`R.string.CP_UnreadBadgeOnBackButton`)
- Custom background in chats (`R.string.CP_CustomWallpapers`)
- Snowflakes in chat (`R.string.NM_CP_SnowflakesInChat`)
- Pencil Icon for Edited Messages (`R.string.AP_ShowPencilIcon`)
- Show forwarded message date (`R.string.CP_ForwardMsgDate`)
- Hide bottom bar in channels (`R.string.CP_HideMuteUnmuteButton`)
- Show weekday by date (`R.string.NM_CP_WeekdayNearDate`)
- CP_HideSendAsChannel (`R.string.CP_HideSendAsChannel`)
- Messages bubble size (`R.string.CP_Messages_Size`)
- Chat menu shortcuts (`R.string.CP_ChatMenuShortcuts`)
- Interaction (`R.string.NM_CP_Header_Interaction`)
- Double tap incoming messages (`R.string.LinkiDoubleTapIncoming`)
- Double tap outgoing messages (`R.string.LinkiDoubleTapOutgoing`)
- Action on message swipe (`R.string.NM_MsgSlideAction`)
- Left button action (`R.string.CP_LeftBottomButtonAction`)
- Quote replies (`R.string.CP_AutoQuoteReplies`)
- Pre-reform Russian spelling (`R.string.NM_CP_PreReformRussian`)
- Render LaTeX formulas (`R.string.NM_CP_LatexRendering`)
- Disable transition to the next channel (`R.string.CP_DisableSwipeToNext`)
- CP_DeleteForAll (`R.string.CP_DeleteForAll`)
- Disable vibration (`R.string.CP_DisableVibration`)
- Scroll intensity to hide keyboard (`R.string.CP_HideKbdOnScroll`)
- Disabled (`R.string.VibrationDisabled`)
- Media (`R.string.NM_CP_Header_Media`)
- Send large photos (2560px) (`R.string.EP_PhotosSize`)
- Play video with volume button press (`R.string.CP_PlayVideo`)
- Auto pause media (`R.string.CP_AutoPauseVideo`)
- Send GIFs with spoiler (`R.string.NM_MSG_GifSpoilers`)
- Double-tap to seek video (seconds) (`R.string.CP_VideoSeekDuration`)
- Off (`R.string.NM_WSB_Status_Off`)
- Telegram Premium (`R.string.TelegramPremium`)
- Disable reactions overlay (`R.string.CP_DisableReactionsOverlay`)
- Disable reaction animation (`R.string.CP_DisableReactionAnim`)
- Disable taps on premium stickers (`R.string.CP_DisablePremStickAnim`)
- Disable premium stickers auto-play (`R.string.CP_DisablePremStickAutoPlay`)
- Notifications (`R.string.SettingsNotifications`)
- Notification Sound (`R.string.NotificationsSound`)
- In-chat vibration (`R.string.CP_VibrateInChats`)
- Direct Share (`R.string.DirectShare`)
- Local pins (`R.string.LC_Pins`)
- Message menu (`R.string.CP_MessageMenu`)
- Message filtering (`R.string.CP_Message_Filtering`)

## CameraPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/CameraPreferencesActivity.java` · ПК-модуль: `linkigram/media/camera_settings.cpp`

- Camera (`R.string.CP_Category_Camera`)
- Camera Type (`R.string.CP_CameraType`)
- Center control buttons (`R.string.CP_CenterCameraControlButtons`)
- Camera button at bottom (`R.string.CP_DisableCam`)
- Video messages (`R.string.CP_Header_Videomessages`)
- Camera for video messages (`R.string.NM_CAM_RoundCamera`)
- Dual camera (`R.string.CP_CameraDualCamera`)
- Start from UW (`R.string.CP_CameraUW`)
- Video quality (`R.string.NM_CAM_VideoQuality`)
- Round video size (`R.string.NM_CAM_RoundVideoSize`)
- Round video bitrate (`R.string.NM_CAM_RoundVideoBitrate`)
- Camera Quality (`R.string.CP_CameraQuality`)
- Camera stabilisation (`R.string.CP_CameraStabilisation`)
- Improvements (`R.string.NM_CAM_Improvements`)
- Optical stabilization (`R.string.NM_CAM_OpticalStabilization`)
- Continuous autofocus (`R.string.NM_CAM_ContinuousFocus`)
- Noise Reduction (`R.string.NM_Camera_NoiseReduction`)
- Face Detection (`R.string.NM_Camera_FaceDetection`)
- High FPS Range (`R.string.NM_Camera_UseHighRange`)

## PrivacyPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/PrivacyPreferencesActivity.java` · ПК-модуль: `linkigram/settings/privacy.cpp`

- Privacy (`R.string.NM_Cat_Privacy`)
- Privacy (`R.string.NM_PR_Header_Privacy`)
- Don\\'t show proxy sponsor (`R.string.NM_PR_HideProxy`)
- Delete Telegram account (`R.string.NM_PR_DeleteAccount`)
- Allow screenshots in protected chats (`R.string.LinkiAllowProtectedScreenshots`)
- Keep opened view-once media (`R.string.LinkiKeepOnce`)
- Saved view-once media (`R.string.LinkiOnceArchive`)
- Chats (`R.string.FilterChats`)
- Hide archived stories (`R.string.NM_PR_HideArchivedStories`)
- Hide archive from chats list (`R.string.NM_PR_HideArchiveList`)
- Ask for biometrics to open chats (`R.string.NM_PR_AskBioOpenChats`)
- Locked chats (`R.string.NM_PR_LockedChats`)
- Ask biometric (`R.string.NM_PR_LockedChatsTtl`)
- Require biometrics to delete chats (`R.string.NM_PR_RequireBiometricsToDelete`)
- Allow use system PIN/Pattern (`R.string.NM_PR_AllowSystemPasscode`)
- Test fingerprint (`R.string.NM_PR_TestFingerprint`)

## AyuMomentsPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/AyuMomentsPreferencesActivity.java` · ПК-модуль: `linkigram/privacy/ghost.cpp`

- AyuMoments (`R.string.NM_AyuMoments`)
- Ghost mode (`R.string.NM_GhostMode`)
- Privacy preset (`R.string.NM_PrivacyPreset`)
- Enable ghost mode (`R.string.NM_GhostMaster`)
- Hide typing status (`R.string.NM_GhostTyping`)
- Hide recording status (`R.string.NM_GhostRecording`)
- Hide online status (`R.string.NM_GhostOnline`)
- Hide read receipts (`R.string.NM_GhostReadReceipts`)
- Hide story views (`R.string.NM_GhostStories`)
- Mark as read after sending (`R.string.NM_GhostReadAfterSend`)
- Go offline after sending (`R.string.NM_GhostOfflineAfterSend`)
- Saving deleted messages (`R.string.NM_Ayu_HeaderSaving`)
- Save deleted messages (`R.string.NM_SaveDeletedMessages`)
- Save in private chats (`R.string.NM_Ayu_SaveDeleted_Private`)
- Save in groups (`R.string.NM_Ayu_SaveDeleted_Groups`)
- Save in channels (`R.string.NM_Ayu_SaveDeleted_Channels`)
- Save messages from bots in private chats (`R.string.NM_Ayu_SaveDeleted_BotUser`)
- Save messages from bots in groups and channels (`R.string.NM_Ayu_SaveDeleted_BotChat`)
- Save in archived chats (`R.string.NM_Ayu_SaveInArchived`)
- Edit history (`R.string.NM_Ayu_HeaderEdits`)
- Save edit history (`R.string.NM_Ayu_SaveEdits`)
- Saving media (`R.string.NM_Ayu_HeaderMedia`)
- Save media files (`R.string.NM_Ayu_SaveMedia`)
- Private chats (`R.string.NM_Ayu_SaveMedia_Private`)
- Public channels (`R.string.NM_Ayu_SaveMedia_PubChannels`)
- Private channels (`R.string.NM_Ayu_SaveMedia_PrivChannels`)
- Public groups (`R.string.NM_Ayu_SaveMedia_PubGroups`)
- Private groups (`R.string.NM_Ayu_SaveMedia_PrivGroups`)
- Local tracking (`R.string.NM_Ayu_HeaderSpy`)
- Save last seen (`R.string.NM_Ayu_SaveLastSeen`)
- Save read date (`R.string.NM_Ayu_SaveReadDate`)
- Show exact last seen (`R.string.NM_LastSeenCache`)
- Deleted messages view (`R.string.NM_Ayu_HeaderView`)
- Translucent deleted messages (`R.string.NM_Ayu_Translucent`)
- Ayu database (`R.string.NM_Ayu_HeaderDb`)
- Export database (`R.string.NM_Ayu_DbExport`)
- Import database (`R.string.NM_Ayu_DbImport`)
- Clear database (`R.string.NM_Ayu_DbClear`)

## MessagesPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessagesPreferencesActivity.java` · ПК-модуль: `linkigram/settings/messages.cpp`

- Messages (`R.string.MessagesSettings`)
- Appearance (`R.string.AP_Header_Appearance`)
- Message menu (`R.string.CP_MessageMenu`)
- Messages bubble size (`R.string.CP_Messages_Size`)
- Direct Share (`R.string.DirectShare`)
- Show forwarded message date (`R.string.CP_ForwardMsgDate`)
- Pencil Icon for Edited Messages (`R.string.AP_ShowPencilIcon`)
- Actions (`R.string.ActionsChartTitle`)
- Message filtering (`R.string.CP_Message_Filtering`)
- Left button action (`R.string.CP_LeftBottomButtonAction`)
- Double tap action (`R.string.CP_DoubleTapAction`)
- Action on message swipe (`R.string.NM_MsgSlideAction`)
- CP_DeleteForAll (`R.string.CP_DeleteForAll`)
- Telegram Premium (`R.string.TelegramPremium`)
- Disable reactions overlay (`R.string.CP_DisableReactionsOverlay`)
- Disable reaction animation (`R.string.CP_DisableReactionAnim`)
- Disable taps on premium stickers (`R.string.CP_DisablePremStickAnim`)
- Disable premium stickers auto-play (`R.string.CP_DisablePremStickAutoPlay`)
- Sending (`R.string.NM_MSG_Header_Sending`)
- Send GIFs with spoiler (`R.string.NM_MSG_GifSpoilers`)

## RecentEmojisStickersPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/RecentEmojisStickersPreferencesActivity.java` · ПК-модуль: `linkigram/chats/recent.cpp`

- Recent emojis and stickers (`R.string.NM_CH_RecentEmojisStickers`)
- Emoji (`R.string.Emoji`)
- Stickers (`R.string.AccDescrStickers`)

## FoldersPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/FoldersPreferencesActivity.java` · ПК-модуль: `linkigram/chats/folders.cpp`

- Folders (`R.string.NM_AP_Folders`)
- NM_FO_TabsHideAllChats (`R.string.NM_FO_TabsHideAllChats`)
- Hide counter in tabs (`R.string.NM_FO_TabsNoCounter`)
- Tab style (`R.string.NM_FO_TabStyle`)
- Add stroke (`R.string.NM_FO_TabStyleStroke`)
- NM_FO_FolderNameInHeader (`R.string.NM_FO_FolderNameInHeader`)
- Show folders at the bottom (`R.string.NM_FO_FoldersAtBottom`)

## BottomTabsPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/BottomTabsPreferencesActivity.java` · ПК-модуль: `linkigram/chats/bottom_tabs.cpp`

- Bottom tabs organizer (`R.string.NM_AP_BottomTabs`)
- Tabs (`R.string.NM_BT_LayoutHeader`)
- Bottom bar mode (`R.string.NM_BT_Mode`)
- Appearance (`R.string.NM_BT_AppearanceHeader`)
- Show tabs titles (`R.string.NM_BT_ShowTabsTitle`)
- Search button in tabs (`R.string.NM_BT_ShowSearchInTabs`)
- Semi-transparent background (`R.string.NM_BT_SemiTransparentTabs`)
- Reset tint color (`R.string.NM_BT_TintReset`)
- Actions (`R.string.NM_BT_ActionsHeader`)
- Always search in chats (`R.string.NM_BT_ForceOpenChats`)
- Reset (`R.string.Reset`)

## NimarkoMediaPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/NimarkoMediaPreferencesActivity.java` · ПК-модуль: `linkigram/media/downloader/settings.cpp`

- Media Downloader (`R.string.NM_DownloadMedia`)
- Options (`R.string.NM_NM_HeaderSettings`)
- Auto-download on URL send (`R.string.NM_NM_AutoDownload`)
- YouTube default format (`R.string.NM_NM_YtFormat`)
- Ask YouTube format (`R.string.NM_NM_YtAsk`)
- Video (`R.string.NM_NM_FormatVideo`)
- Audio (`R.string.NM_NM_FormatAudio`)

## BannerPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/BannerPreferencesActivity.java` · ПК-модуль: `linkigram/profile/banners/settings.cpp`

- Banners (`R.string.NM_BAN_Title`)
- Banner system (`R.string.NM_BAN_Enable`)
- Global banner (`R.string.NM_BAN_GlobalHeader`)
- Status (`R.string.NM_BAN_StatusLabel`)
- Change global banner (`R.string.NM_BAN_ChangeGlobal`)
- Submit for moderation (`R.string.NM_BAN_SubmitModeration`)
- Avatar (`R.string.NM_BAN_AvatarSectionHeader`)
- Hide avatar (`R.string.NM_BAN_HideAvatar`)
- Refresh status (`R.string.NM_BAN_RefreshStatus`)
- Quality (`R.string.NM_BAN_QualityHeader`)
- Lite mode (`R.string.NM_BAN_LiteMode`)
- Local banner (`R.string.NM_BAN_LocalHeader`)
- Banner from avatar (`R.string.NM_BAN_AvatarBanner`)
- Choose local banner (`R.string.NM_BAN_PickLocal`)
- Delete local banner (`R.string.NM_BAN_DeleteLocal`)

## DebugPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/DebugPreferencesActivity.java` · ПК-модуль: `linkigram/settings/debug.cpp`

- Debug (`R.string.NM_DBG_Title`)
- Miscellaneous (`R.string.NM_DBG_Header_Misc`)
- Show RPC errors (`R.string.NM_DBG_ShowRPCErrors`)
- Default time style in chats (`R.string.NM_DBG_OldTimeStyle`)
- Microphone audio source (`R.string.NM_DBG_AudioSource`)
- Use Jackson JSON provider (`R.string.NM_DBG_JacksonJSONProvider`)
- Play GIFs as videos (`R.string.NM_DBG_PlayGIFsAsVideos`)
- Hide video timestamp (`R.string.NM_DBG_HideVideoTimestamp`)
- Replace punctuation marks (`R.string.NM_DBG_ReplacePunctuation`)
- EditText suggestions fix (`R.string.NM_DBG_EditTextFix`)
- Show accounts in drawer (`R.string.NM_DBG_ShowAccounts`)
- Send videos at max quality (`R.string.NM_DBG_SendMaxQuality`)
- Danger zone (`R.string.NM_DBG_Header_Danger`)
- Force crash (`R.string.NM_DBG_ForceCrash`)

## ExperimentalPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/ExperimentalPreferencesActivity.java` · ПК-модуль: `linkigram/settings/experimental.cpp`

- Experimental (`R.string.NM_EXP_Title`)
- Beta toggles (`R.string.NM_EXP_Header_Beta`)
- Edge-to-edge mode (`R.string.NM_EXP_EdgeToEdge`)
- Disable corner rounding (`R.string.NM_EXP_NoRounding`)
- Discuss instead of mute on swipe (`R.string.NM_EXP_DiscussInsteadOfMute`)

## NimarkoTextAnimPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/NimarkoTextAnimPreferencesActivity.java` · ПК-модуль: `linkigram/effects/text_animation.cpp`

- Text animation (`R.string.NM_TA_Title`)
- Animation (`R.string.NM_TA_HeaderMain`)
- Enable text animation (`R.string.NM_TA_Master`)
- Effects (`R.string.NM_TA_HeaderEffects`)
- Appearance (`R.string.NM_TA_Appear`)
- Smooth cursor (`R.string.NM_TA_Cursor`)
- Delete particles (`R.string.NM_TA_Delete`)
- Spoiler reveal (`R.string.NM_TA_Spoiler`)

## BackgroundPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/BackgroundPreferencesActivity.java` · ПК-модуль: `linkigram/theme/background.cpp`

- App background (`R.string.NM_BG_Title`)
- Background (`R.string.NM_BG_Header`)
- Custom background (`R.string.NM_BG_Enable`)
- Choose image (`R.string.NM_BG_Pick`)
- Dimming (`R.string.NM_BG_Dim`)
- Card readability (`R.string.NM_BG_Cards`)
- Remove image (`R.string.NM_BG_Remove`)
- Glass interface (`R.string.NM_GLASS_Header`)
- Linki Ass (`R.string.NM_GLASS_Liquid`)
- Chat glass transparency (`R.string.NM_GLASS_ChatLevel`)

## MessageMenuItemsPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessageMenuItemsPreferencesActivity.java` · ПК-модуль: `linkigram/chat/message_menu.cpp`

- Message menu items (`R.string.NM_MM_MessageMenuItems`)
- Save for notifications (`R.string.SaveForNotifications`)
- Copy (`R.string.Copy`)
- Repeat (`R.string.Repeat`)
- Reply (`R.string.Reply`)
- Save to gallery (`R.string.SaveToGallery`)
- Copy photo (`R.string.NM_MI_CopyPhoto`)
- Copy photo as sticker (`R.string.NM_MI_CopyPhotoAsSticker`)
- Save to downloads (`R.string.SaveToDownloads`)
- Share (`R.string.ShareFile`)
- Clear from cache (`R.string.NM_MI_ClearFromCache`)
- Forward (`R.string.Forward`)
- Forward without authorship (`R.string.NM_MI_ForwardWoAuthorship`)
- Search Messages (`R.string.AvatarPreviewSearchMessages`)
- Save to Saved Messages (`R.string.NM_MI_SaveToSaved`)
- Report (`R.string.ReportChat`)
- Forward without caption (`R.string.NM_MI_ForwardWoCaption`)
- Download sticker (`R.string.NM_MI_DownloadSticker`)
- Custom emoji (`R.string.AccDescrCustomEmoji`)
- Details (`R.string.NM_MI_Details`)

## DrawerPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/DrawerPreferencesActivity.java` · ПК-модуль: `linkigram/window/drawer_settings.cpp`

- Side menu settings (`R.string.LF_Menu`)
- Side menu (drawer) (`R.string.NM_Drawer_NavigationDrawer`)
- Side menu appearance (`R.string.LF_DrawerStyle`)
- Reset (`R.string.Reset`)
- Folders (`R.string.CP_Filters_Header`)

## ScreenAppearanceActivity
Текущий файл: `app/nimarkogram/messenger/preferences/ScreenAppearanceActivity.java` · ПК-модуль: `linkigram/window/drawer_settings.cpp`

- Side menu appearance (`R.string.LF_DrawerStyle`)
- Compact spacing (`R.string.LF_Compact`)
- Show labels and details (`R.string.LF_Labels`)
- Accent color (`R.string.LF_Accent`)
- Reset (`R.string.Reset`)

## AiSettingsActivity
Текущий файл: `app/exteraless/ai/ui/AiSettingsActivity.java` · ПК-модуль: `linkigram/ai/settings.cpp`

- AI Assistant (`R.string.OEAiTitle`)
- Endpoint (`R.string.OEAiUrl`)
- API key (`R.string.OEAiKey`)
- History cleared (`R.string.OEAiHistoryCleared`)
- Set the endpoint, model and API key first (`R.string.OEAiNotConfigured`)
- works (`R.string.OEAiTestOk`)
- Request failed (`R.string.OEAiFailed`)
- Provider (`R.string.OEAiProvider`)
- Custom (`R.string.OEAiProviderCustom`)
- Endpoint + model (`R.string.OEAiPresets`)
- Save the current pair (`R.string.OEAiPresetSave`)
- Name for this pair (`R.string.OEAiPresetName`)
- Off (`R.string.OEAiReasoningOff`)
- Low (`R.string.OEAiReasoningLow`)
- Medium (`R.string.OEAiReasoningMedium`)
- High (`R.string.OEAiReasoningHigh`)
- Reasoning (`R.string.OEAiReasoning`)
- Role (`R.string.OEAiRole`)
- Add your own (`R.string.OEAiRoleAdd`)
- Name (`R.string.OEAiRoleName`)
- Instruction for the model (`R.string.OEAiRolePrompt`)
- not set (`R.string.OEAiKeyNotSet`)
- Temperature (`R.string.OEAiTemperature`)
- Precise (`R.string.OEAiTemperatureExact`)
- Varied (`R.string.OEAiTemperatureVaried`)
- Service (`R.string.OEAiServiceHeader`)
- Generation (`R.string.OEAiGenerationHeader`)
- Stream the answer (`R.string.OEAiStreaming`)
- Keep conversation history (`R.string.OEAiSaveHistory`)
- Disable tools (`R.string.OEAiDisableTools`)
- Plugin review sends the code in one request, as before: only the first 48 KB, embedded data is not decoded (`R.string.OEAiDisableToolsInfo`)
- Model (`R.string.OEAiModel`)
- Get an API key (`R.string.OEAiGetKey`)
- Check the connection (`R.string.OEAiTest`)
- Clear history (`R.string.OEAiClearHistory`)
- Any OpenAI-compatible endpoint works. The key is stored on this device only and is sent to the endpoint you set. (`R.string.OEAiServiceInfo`)
- Lower temperature gives steadier answers, higher gives more varied ones. (`R.string.OEAiGenerationInfo`)

## InfoCardsPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/infocards/preferences/InfoCardsPreferencesActivity.java` · ПК-модуль: `linkigram/infocards/settings.cpp`

- Quick info (`R.string.NM_CARDS_Title`)
- General (`R.string.NM_CARDS_GeneralHeader`)
- Enable (`R.string.NM_CARDS_Enable`)
- Infinite scrolling (`R.string.NM_CARDS_InfiniteScroll`)
- Auto-scroll (`R.string.NM_CARDS_AutoScroll`)
- Active cards (`R.string.NM_CARDS_ActiveHeader`)
- Hidden cards (`R.string.NM_CARDS_HiddenHeader`)

## WsBypassPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/wsbypass/preferences/WsBypassPreferencesActivity.java` · ПК-модуль: `linkigram/net/wsbypass/settings.cpp`

- Bypass (`R.string.NM_WSB_Title`)
- Connection (`R.string.NM_SettingsSectionConnection`)
- Enable bypass (`R.string.NM_WSB_Enable`)
- Route mode (`R.string.NM_WSB_RouteHeader`)
- Via relay (`R.string.NM_WSB_RouteRelay`)
- tg-ws-linki (`R.string.NM_WSB_RouteTgws`)
- X-CFG (built-in VPN) (`R.string.NM_WSB_RouteXcfg`)
- Miscellaneous (`R.string.NM_WSB_MiscHeader`)
- Route calls through bypass (`R.string.NM_WSB_VoIP_Enable`)
- Suspend under VPN (`R.string.NM_WSB_SuspendOnVpn`)

## MessageMenuPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessageMenuPreferencesActivity.java` · ПК-модуль: `linkigram/chat/message_menu.cpp`

- Message menu (`R.string.CP_MessageMenu`)

## MessageFiltersPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessageFiltersPreferencesActivity.java` · ПК-модуль: `linkigram/chat/message_filters.cpp`

- Message filtering (`R.string.NM_MF_Title`)

## MessageMenuOrderPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessageMenuOrderPreferencesActivity.java` · ПК-модуль: `linkigram/chat/message_menu.cpp`

- Reorder menu items (`R.string.NM_Menu_Reorder`)

## MessagesAndProfilesPreferencesActivity
Текущий файл: `app/nimarkogram/messenger/preferences/MessagesAndProfilesPreferencesActivity.java` · ПК-модуль: `linkigram/profile/extras.cpp`

- Messages and profiles (`R.string.CP_ProfileReplyBackground`)
- Format time with seconds (`R.string.NM_MP_ShowSeconds`)
- Disable premium statuses (`R.string.NM_MP_DisablePremiumStatuses`)
- Background in replies (`R.string.NM_MP_ReplyBackground`)
- Custom colors in replies and links preview (`R.string.NM_MP_ReplyCustomColors`)
- Emoji in replies and links preview (`R.string.NM_MP_ReplyBackgroundEmoji`)
- Personal channel (`R.string.NM_MP_ProfileChannelPreview`)
- Show ID and DC in profile (`R.string.NM_MP_ShowIdDc`)
- Date of Birth (`R.string.NM_MP_ProfileBirthDatePreview`)
- Business hours and location (`R.string.NM_MP_ProfileBusinessPreview`)
- Background in profile (`R.string.NM_MP_ProfileBackgroundColor`)
- Emoji in profile background (`R.string.NM_MP_ProfileBackgroundEmoji`)

## IconPackSelectorActivity
Текущий файл: `app/nimarkogram/messenger/preferences/IconPackSelectorActivity.java` · ПК-модуль: `linkigram/theme/icon_pack.cpp`

- Icon pack (`R.string.AP_IconReplacements`)

## PluginsActivity
Текущий файл: `app/nimarkogram/messenger/plugins/ui/PluginsActivity.java` · ПК-модуль: `только Android`

- Plugins (`R.string.Plugins`)


---

# 7. Только Android (не входит в ПК-клиент)

Плагинная подсистема целиком остаётся Android-only (см. `desktop/README.md`):
Chaquopy/Python/Pine/DEX, нативные JNI-мосты, активити/сервисы/ассеты Android.
Портировать их в `tdesktop` не требуется.

| Компонент | Тип | Где (Android) |
| --- | --- | --- |
| Движок плагинов (Python) | Сервис | `app/nimarkogram/messenger/plugins/PythonPluginsEngine.java`, `PluginsController.java`, `PluginServices.java`, `PluginSourceScanner.java`, `JsonUtils.java`, `AppEvent.java` |
| Модель плагина/прав | Данные | `Plugin.java`, `PluginPermissions.java`, `PluginsConstants.java`, `PluginDebugLog.java` |
| Мост dev-установки | Интеграция | `plugins/PluginDevInstallBridge.java` |
| UI плагинов | Экраны | `plugins/ui/PluginsActivity.java`, `PluginSettingsActivity.java`, `PluginsInfoActivity.java`, `PluginOverlayHost.java`, `PluginUiRegistry.java`, `PluginUiDiskExecutor.java`, `PluginViewListener.java`, `PluginDialogCallback.java`, `components/` |
| Python-пакеты | Ресурсы | `app/elyx_runtime` (Python), Chaquopy-конфигурация в `TMessagesProj/build.gradle` |
| ИИ-агент плагинов | Экран | `plugins/ui/PluginAgentActivity.java`, `PluginAgentPrompt.java` — **исключён из документа** |
