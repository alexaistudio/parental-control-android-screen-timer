# Исследование Android parental control для TV, планшетов и телефонов

Проверено 3 сентября 2026 года.

## Существующие решения

| Решение | Что умеет | Что взято для Android Screen Timer |
|---|---|---|
| Google TV Kids profile | Дневной лимит, bedtime, bonus time, блокировка выхода из детского профиля | Понятная дневная квота и добавление времени, но без зависимости от детского Google-профиля |
| tvusage | PIN, лимит устройства/приложений, часы использования, группы, перерывы, статистика, облачная синхронизация, защита удаления | Та же практическая схема active-app detection + overlay, но только локальное минимальное ядро без аккаунта, аналитики и сети |

TVUsage публично описывает сочетание Usage Stats, accessibility-службы, обычного overlay, foreground service и boot receiver. Для минимальной задачи достаточно одной accessibility-службы: событие содержит имя активного пакета, а `TYPE_ACCESSIBILITY_OVERLAY` не требует отдельного разрешения «поверх других приложений». Содержимое окон отключено через `canRetrieveWindowContent=false`.

## Платформенные решения

- Android разрешает accessibility-службе подписаться на выбранные типы событий и пакеты; служба обязана быть объявлена с `BIND_ACCESSIBILITY_SERVICE` и включается пользователем вручную: <https://developer.android.com/guide/topics/ui/accessibility/views/service>
- Accessibility overlay предназначен в том числе для общей UI-службы поверх текущего окна: <https://developer.android.com/reference/android/accessibilityservice/AccessibilityService>
- `BOOT_COMPLETED`, USB attach и media mounted перечислены среди исключений из запрета manifest implicit broadcasts: <https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions>
- Google TV Kids profiles уже имеют дневные лимиты, bedtime и bonus time, но наличие зависит от устройства, региона и профиля: <https://support.google.com/googletv/answer/10070481>
- Текущий список возможностей tvusage и описание его accessibility-механизма: <https://play.google.com/store/apps/details?id=in.codeseed.tvusage>
- Политика Google Play требует отдельного заметного раскрытия и положительного согласия для accessibility-службы, если приложение не является accessibility tool: <https://support.google.com/googleplay/android-developer/answer/10964491>
- GitHub Releases API возвращает метаданные ассетов, включая SHA-256 digest: <https://docs.github.com/en/rest/releases/assets>
- Android предоставляет сертификаты установленного и архивного APK через `SigningInfo`: <https://developer.android.com/reference/android/content/pm/SigningInfo>
- Загрузка APK выполняется системным `DownloadManager`, а установка остаётся отдельным действием пользователя: <https://developer.android.com/reference/android/app/DownloadManager>
- Настоящий `setUninstallBlocked` доступен Device/Profile Owner, поэтому обычный Device Admin рассматривается только как дополнительный барьер: <https://developer.android.com/reference/android/app/admin/DevicePolicyManager>
- Официальный ADB-путь требует включить Developer options и подтвердить RSA-ключ; для USB отдельно включается USB debugging: <https://developer.android.com/tools/adb#Enabling>
- Android 11+ на телефонах/планшетах и Android TV 13+ поддерживают Wireless debugging с pairing-кодом или QR; оба устройства должны быть в одной Wi‑Fi сети, а обнаружение использует mDNS: <https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi>
- Для Android 10 и старше стандартный Wi‑Fi ADB требует первичного USB-подключения и `adb tcpip 5555`, если OEM не дал собственный переключатель сетевой отладки: <https://developer.android.com/tools/adb#wireless-android10-and-lower>
- Мобильный ADB-клиент построен на `libadb-android` 3.1.1, который поддерживает TCP, TLS pairing и shell/services; библиотека выбрана по Apache-2.0 варианту двойной лицензии: <https://github.com/MuntashirAkon/libadb-android>

## Почему нет root, Usage Access и Device Owner

Root противоречит требованию и повышает риск для телевизора. Usage Access хорошо подходит для истории, но не нужен для реального времени, когда уже используется accessibility event. Device Owner даёт более сильную защиту от обхода, но обычно требует factory reset/ADB provisioning и не является универсальной установкой обычного APK. Поэтому выбран обратимый PIN-барьер плюс опциональный обычный Device Admin с честно указанными ограничениями.

## Почему два APK и почему всё равно нужен один шаг на целевом устройстве

Обычное приложение на телефоне не имеет права незаметно включить ADB на другом Android-устройстве. Поэтому продукт разделён на блокировщик и родительский установщик, но не обещает невозможного: владелец один раз открывает Developer options и подтверждает системное сопряжение. После этого телефон действительно выполняет поиск, установку, настройку и проверку сам, без Bugjaeger или компьютера. Для старых устройств без сетевого ADB остаётся прямая установка блокировщика.
