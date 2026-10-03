<p align="center">
  <a href="https://t.me/linkireleases">
    <img alt="Telegram-канал релизов" src="https://img.shields.io/badge/%F0%9F%93%A3%20%D0%BA%D0%B0%D0%BD%D0%B0%D0%BB%20%D1%80%D0%B5%D0%BB%D0%B8%D0%B7%D0%BE%D0%B2-2AABEE?style=flat-square&logo=telegram&logoColor=white">
  </a>
  <a href="https://www.linkigram.bond">
    <img alt="Сайт проекта" src="https://img.shields.io/badge/%D1%81%D0%B0%D0%B9%D1%82-www.linkigram.bond-3DDC84?style=flat-square">
  </a>
  <img alt="Лицензия" src="https://img.shields.io/badge/license-GPL--2.0-6F42C1?style=flat-square">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square">
</p>

# LinkiGram

**Неофициальный клиент Telegram для Android.** Форк [Telegram для Android](https://github.com/DrKLO/Telegram) с ghost mode, инструментами приватности, фоновым прокси и полноценной плагинной платформой.

Сайт проекта: **https://www.linkigram.bond**

## 📢 Канал релизов

**Все версии и анонсы выходят сюда → [@linkireleases](https://t.me/linkireleases)**

Это основной канал обновлений: новые релизы появляются там первыми, раньше, чем в репозитории. Там же обсуждение и сборки.

**[Подписаться на @linkireleases](https://t.me/linkireleases)**

## Что внутри

- **Ghost mode** — скрытие статуса «печатает», последнего просмотра и прочих индикаторов присутствия.
- **Гибкая настройка** — цвета Monet, наборы иконок, вкладки, заголовки чатов и профилей.
- **Приватность и биометрия** — дополнительные инструменты приватности, блокировка биометрией, фильтрация чатов.
- **Фоновый прокси** — соединение не засыпает в фоне, уведомления не зависят от FCM.
- **Медиа и камера** — улучшенные сценарии CameraX, видеосообщения, истории и буфер обмена.
- **Плагины** — Python- и DEX-плагины, перехваты, зависимости, безопасный жизненный цикл.
- **Сетевые инструменты** — настраиваемые адреса сервисов и параметры транспорта звонков.

## Установка

Готовые APK лежат в [Releases](https://github.com/timaa130704/LinkiGram/releases/latest). Единый standalone APK поддерживает ARM64 и ARMv7, требуется Android 7.0 или новее. Установка поверх предыдущей версии не теряет данные.

Сборка из исходников: **[docs/BUILDING.md](docs/BUILDING.md)** — нужны JDK 17, Android SDK 36, NDK 26.3.11579264, CMake 3.22.1.

## Документы

| Файл | О чём |
| --- | --- |
| [docs/BUILDING.md](docs/BUILDING.md) | Полная инструкция по сборке и решение типичных проблем |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Как участвовать в проекте |
| [SECURITY.md](SECURITY.md) | Как сообщать об уязвимостях |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | Что изменилось в последних версиях |

## Благодарности

- [Telegram для Android](https://github.com/DrKLO/Telegram) — исходный клиент, на котором построен проект.
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) — отдельные открытые компоненты интерфейса с сохранением происхождения в коде.
- [Pine](https://github.com/canyie/pine) — движок перехватов, используемый средой плагинов.

---

> LinkiGram — независимый проект. Он не связан с Telegram Messenger Inc. и не одобрен этой компанией.

## Лицензия

[GNU General Public License v2.0](LICENSE). Telegram и встроенные сторонние компоненты сохраняют собственные авторские права и условия лицензий.