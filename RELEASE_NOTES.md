# LinkiGram v1.3 — Релиз

## 📌 Общая информация
- **Версия**: 1.3
- **Дата релиза**: 19 августа 2026
- **Тип релиза**: Minor update

## 🔄 Основные изменения
- **Обновлена версия приложения** с 1.2 до 1.3.
- **Собрана Standalone APK** для ARM64 и ARMv7.
- **Поддержка новых фич** и улучшений.

## 📦 Артефакты
- `LinkiGram-1.3.apk` — Standalone-версия приложения (ARM64 + ARMv7).

## 📝 Как установить
1. Скачайте `app.apk`.
2. Установите на Android-устройство (версия 7.0+).
3. При первом запуске введите свои Telegram API ID и HASH.

## 🔧 Сборка из исходников
```bash
# Клонируйте репозиторий
git clone --recursive https://github.com/timaa130704/LinkiGram.git
cd LinkiGram

# Обновите версию в private.properties.example
cp private.properties.example private.properties
# Добавьте свои API ID и HASH

# Соберите APK
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
# APK будет в TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

## 📄 Лицензия
LinkiGram распространяется под лицензией **GPL-2.0**.

## 🤝 Вклад в проект
- Баги и фичи: [CONTRIBUTING.md](CONTRIBUTING.md)
- Безопасность: [SECURITY.md](SECURITY.md)

---
🚀 **Спасибо за использование LinkiGram!**