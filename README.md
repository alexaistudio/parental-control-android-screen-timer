<div align="center">

<img src="docs/assets/app-icon.svg" alt="Иконка Android Screen Timer" width="112" height="112">

# ⏱️ Родительский контроль — Android Screen Timer

<img src="poster.jpg" alt="Родительский контроль — Android Screen Timer" width="100%">

### Телевизор, планшет или телефон — экранное время под контролем.

**Бесплатный семейный ограничитель времени для Android**<br>
с кодом родителя, напоминаниями, выбором приложений, маскировкой и защитой от удаления.

[![CI](https://github.com/alexaistudio/parental-control-android-screen-timer/actions/workflows/ci.yml/badge.svg)](https://github.com/alexaistudio/parental-control-android-screen-timer/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/alexaistudio/parental-control-android-screen-timer?sort=semver&display_name=tag&label=версия)](https://github.com/alexaistudio/parental-control-android-screen-timer/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/alexaistudio/parental-control-android-screen-timer/total?label=скачиваний)](https://github.com/alexaistudio/parental-control-android-screen-timer/releases)
[![License](https://img.shields.io/badge/лицензия-PolyForm_Strict_1.0.0-2563eb)](LICENSE.md)

![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
![Android TV](https://img.shields.io/badge/Android_TV-поддерживается-3DDC84)
![Google TV](https://img.shields.io/badge/Google_TV-поддерживается-4285F4?logo=google&logoColor=white)
![Tablet](https://img.shields.io/badge/планшеты-поддерживаются-2563eb)
![Phone](https://img.shields.io/badge/телефоны-поддерживаются-7c3aed)
![Controls](https://img.shields.io/badge/управление-сенсор_%7C_пульт-f59e0b)
![Languages](https://img.shields.io/badge/языки-RU_%7C_EN-7c3aed)
![Offline](https://img.shields.io/badge/работает-локально-059669)
![No ads](https://img.shields.io/badge/реклама-нет-16a34a)
![No tracking](https://img.shields.io/badge/слежка-нет-16a34a)

[**⬇️ Скачать готовый APK**](https://github.com/alexaistudio/parental-control-android-screen-timer/releases/latest) ·
[Как включить максимальную защиту](docs/GUIDE.ru.md#максимальная-защита-от-удаления-device-owner) ·
[💚 Поддержать разработку](#поддержать-разработку) ·
[🇬🇧 Full English README](README.en.md)

</div>

---

## Ограничьте время на девайсах своему ребёнку!!!

Задайте, сколько времени можно смотреть или играть. Приложение покажет остаток, напомнит о перерыве и закроет выбранные приложения, когда время закончится. Новый день — новый лимит по времени устройства.

## Что умеет

- ⏱️ **Ограничивать время** — на всём устройстве или только в выбранных приложениях.
- ➕ **Добавлять минуты с разрешения родителя** — от 10 минут до 2 часов.
- 🔐 **Принимать код с телефона** — он действует до 5 минут, чтобы успеть ввести его с пульта. Есть и постоянный PIN.
- 🔔 **Напоминать о перерыве** — каждые 10, 20 или 30 минут.
- 👨‍👩‍👧‍👦 **Подключать нескольких родителей** — через QR-код в настройках.
- 🥷 **Менять имя и значок** — 10 встроенных вариантов, например «Часы» или «Калькулятор».
- 🛡️ **Защищать доступ к настройкам** — при желании эту защиту можно отключить.

Работает на **Android 6.0 и новее**: телевизорах, планшетах и телефонах. Управление — пультом или касанием. Интерфейс — русский и английский. Без рекламы, аккаунтов и облака.

## Скачать и начать

В [последнем релизе](https://github.com/alexaistudio/parental-control-android-screen-timer/releases/latest) два APK. Выберите подходящий:

| Куда устанавливаете | Какой файл скачать |
| --- | --- |
| На телевизор, планшет или телефон ребёнка | **AndroidScreenTimer-1.4.9.apk** — сам ограничитель |
| На Android-телефон родителя, чтобы установить ограничитель на другое устройство | **AndroidScreenTimer-Parent-1.4.9.apk** — установщик со встроенным ограничителем |

**Напрямую:** установите ограничитель на устройство ребёнка, откройте его и выполните первоначальную настройку:

1. Отсканируйте QR-код на телефоне родителя приложением для кодов, например Google Authenticator или Aegis.
2. Задайте резервный PIN, дневной лимит и выберите приложения.
3. Включите службу контроля в специальных возможностях Android и проверьте её статус в приложении.

**С телефона родителя:** установщик поможет передать приложение на другое устройство по Wi-Fi. На принимающем устройстве сначала нужно включить отладку. Это не установка «на любой телевизор одним нажатием»: доступность подключения зависит от прошивки.

[📖 Пошаговая установка: Wi-Fi или напрямую](docs/GUIDE.ru.md#установка)

## Что важно знать

- **Случайно открыли защищённые настройки?** Нажмите «Назад» или кнопку «На главный экран» в окне кода. Для выхода PIN не нужен.
- **Маскировка не делает приложение невидимым.** Она меняет значок и название на главном экране, но не скрывает его из системного списка.
- **Защита от удаления зависит от Android.** Для системного запрета удаления нужен дополнительный режим Device Owner. [Как его включить](docs/GUIDE.ru.md#максимальная-защита-от-удаления-device-owner).
- **Сохраните резервный и аварийный коды.** Обычная флешка или зарядка не снимает блокировку. Восстановление через специальный файл на флешке можно отключить.

## Инструкции и помощь

- [Подробные настройки, восстановление и ограничения](docs/GUIDE.ru.md)
- [Не включается контроль? Начните с инструкции установки](docs/GUIDE.ru.md#вариант-2--установить-apk-напрямую)
- [Сообщить об ошибке](https://github.com/alexaistudio/parental-control-android-screen-timer/issues) — укажите модель устройства и версию приложения. На телефоне родителя журнал можно скопировать или сохранить в TXT; на ТВ — перенести через QR. Перед публикацией проверьте журнал: в нём могут быть локальные IP-адреса и сведения об устройстве.
- [Что нового](CHANGELOG.md)
- Для разработчиков: [сборка](docs/GUIDE.ru.md#сборка) · [архитектура](docs/ARCHITECTURE.md) · [данные и разрешения](docs/GUIDE.ru.md#данные-и-разрешения)

## Поддержать разработку

Android Screen Timer остаётся бесплатным для семьи. Если приложение оказалось полезным, разработку можно добровольно поддержать через **USDT TRC-20**.

**Сеть:** TRON<br>
**Адрес:** `TMoM4t1JsevXo42cRBiYue51NXrsjuGhqd`

<p align="center">
  <img src="docs/usdt-trc20-qr.png" alt="QR-код адреса USDT TRC-20 в сети TRON" width="240">
</p>

QR-код содержит только указанный выше адрес. Перед отправкой убедитесь, что в кошельке выбрана сеть **TRON (TRC-20)**.

## Лицензия

Проект распространяется по условиям [PolyForm Strict License 1.0.0](LICENSE.md). Допускается немодифицированное некоммерческое использование; официальные условия лицензии имеют приоритет над кратким описанием.

Уведомления и лицензии библиотек мобильного ADB-установщика перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
