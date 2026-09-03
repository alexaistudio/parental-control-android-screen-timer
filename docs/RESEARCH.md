# Исследование Android parental control для TV, планшетов и телефонов

Проверено 15 августа 2026 года.

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

## Почему нет root, Usage Access и Device Owner

Root противоречит требованию и повышает риск для телевизора. Usage Access хорошо подходит для истории, но не нужен для реального времени, когда уже используется accessibility event. Device Owner даёт более сильную защиту от обхода, но обычно требует factory reset/ADB provisioning и не является универсальной установкой обычного APK. Поэтому выбран обратимый PIN-барьер плюс опциональный обычный Device Admin с честно указанными ограничениями.
