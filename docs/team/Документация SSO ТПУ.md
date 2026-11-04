# Единая авторизация ТПУ

# Введение

Данный сервис позволяет провести авторизацию пользователей ТПУ в приложении, сохранив при этом учетные данные пользователей.
 Адрес сервиса в сети интернет: https://oauth.tpu.ru

# Описание сервиса

Данный сервис авторизации, позволяющий выдать одному сервису (приложению) права на доступ к ресурсам пользователя на другом сервисе. Протокол избавляет от необходимости доверять приложению логин и пароль, а также позволяет выдавать ограниченный набор прав, а не все сразу.

#### Авторизация для приложений, имеющих серверную часть

[![64e01804.png](https://devdocs.tpu.ru/uploads/images/gallery/2024-10/scaled-1680-/64e01804.png)](https://devdocs.tpu.ru/uploads/images/gallery/2024-10/64e01804.png)

1. Редирект на страницу авторизации
2. На странице авторизации у пользователя запрашивается подтверждение выдачи прав
3. В случае согласия пользователя, браузер редиректится на URL, указанный при открытии страницы авторизации, с добавлением в GET-параметры специального ключа — *authorization code*
4. Сервер приложения выполняет POST-запрос с полученным *authorization code* в качестве параметра. В результате этого запроса возвращается *access token*

*Это самый сложный вариант авторизации, но только он позволяет сервису однозначно установить приложение, обращающееся за авторизацией (это происходит при коммуникации между серверами на последнем шаге). Во всех остальных вариантах авторизация происходит полностью на клиенте и по понятным причинам возможна маскировка одного приложения под другое. Это стоит учитывать при внедрении OAuth-аутентификации в API сервисов.*

#### *Авторизация полностью клиентских приложений*

*[![5945f6c8.png](https://devdocs.tpu.ru/uploads/images/gallery/2024-10/scaled-1680-/5945f6c8.png)](https://devdocs.tpu.ru/uploads/images/gallery/2024-10/5945f6c8.png)*

1. Открытие встроенного браузера со страницей авторизации
2. У пользователя запрашивается подтверждение выдачи прав
3. В случае согласия пользователя, браузер редиректится на страницу-заглушку во фрагменте (после #) URL которой добавляется *access token*
4. Приложение перехватывает редирект и получает *access token* из адреса страницы

 *Этот вариант требует поднятия в приложении окна браузера, но не требует серверной части и дополнительного вызова сервер-сервер для обмена authorization code на access token.*

# Восстановление предыдущей авторизации

***Access token*** имеет ограниченный срок годности (30 минут), так как он передается по открытым каналам. Чтобы не заставлять пользователя проходить авторизацию после истечения срока действия ***access token***'а, в дополнение к ***access token***'у возвращается еще ***refresh token***. По нему можно получить ***access token*** с помощью HTTP-запроса.

##### **Описание процесса [восстановление авторизации](https://devdocs.tpu.ru/books/edinaia-avtorizaciia-tpu/page/vosstanovlenie-avtorizacii "Восстановление авторизации")**

# OpenID Connect

OpenID Connect (*OIDC*) — это безопасный механизм, позволяющий приложению связаться со службой идентификации, чтобы получить необходимые данные о пользователе и вернуть их обратно в приложение, обеспечив полную защиту данных.

# Термины и понятия

Используемые сокращения при работе с сервером авторизации

<table border="1" id="bkmrk-%D0%9F%D0%B0%D1%80%D0%B0%D0%BC%D0%B5%D1%82%D1%80-%D0%9E%D0%BF%D0%B8%D1%81%D0%B0%D0%BD%D0%B8%D0%B5-cl" style="border-collapse: collapse; width: 100%;"><colgroup><col style="width: 16.3691%;"></col><col style="width: 83.7501%;"></col></colgroup><thead><tr><td>Параметр</td><td>Описание</td></tr></thead><tbody><tr><td>***client\_id***</td><td>[Идентификатор приложения](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/nastroiki-edinoi-avtorizacii)</td></tr><tr><td>***client\_secret***</td><td>[Защищенный ключ приложения](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/nastroiki-edinoi-avtorizacii)</td></tr><tr><td>***redirect\_uri***</td><td>Адрес, на который будет передан code (домен указанного адреса должен соответствовать основному домену в настройках приложения и перечисленным значениям в списке доверенных redirect uri — адреса сравниваются до корневого домена).</td></tr><tr><td>***response\_type***</td><td>Тип ответа, по умолчанию значение ***code***.</td></tr><tr><td>**state**</td><td>Произвольная строка, которая будет возвращена вместе с результатом авторизации.</td></tr><tr><td>***grant\_type***</td><td>[Способ авторизации](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/sposoby-avtorizacii-i-poluceniia-dostupa)</td></tr></tbody></table>

# Рабочий процесс



# Процесс авторизации

Для авторизации пользователя через единую авторизацию пользователей ТПУ необходимо выполнить следующие действия:

1. Создать приложение (перейти по ссылке [https://api.tpu.ru/dashboard](https://api.tpu.ru/dashboard)).
2. Создать платформу и указать версию приложения (получить **api\_key**).
3. Настроить авторизацию через Oauth (получить **client\_id** и **client\_secret**) - указать доверительные домены (каждый на отдельной строке)
4. Дождаться проверки приложения со стороны ТПУ и начать пользоваться.
5. Прописать все IP адреса серверов (и локальных машин разработчиков при необходимости) в доверенную зону на которую будет отправлен ответ от сервера авторизации (https://api.tpu.ru/dashboard МЕНЮ-&gt;Авторизация-&gt;Oauth2 Oauyh2 Сервер)

#### Процесс авторизации

1\. Необходимо перенаправить браузер пользователя по адресу используя метод GET

<p class="callout info">**https://oauth.tpu.ru/authorize**</p>

 передав следующие параметры

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-client" style="border-collapse: collapse; width: 100%; height: 263.61px;"><colgroup><col style="width: 26.6072%;"></col><col style="width: 73.512%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**Ключ**</td><td style="height: 29.7969px;">**Значение**</td></tr></thead><tbody><tr style="height: 29.7969px;"><td style="height: 29.7969px;">***client\_id*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">Наименование клиента на страничке вашего приложения https://api.tpu.ru/dashboard МЕНЮ-&gt;Авторизация-&gt;Oauth2 Oauyh2 Сервер</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">***redirect\_uri*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">Ссылка, на которую после успешной авторизации сервер делает редирект пользователя.

> домен указанного адреса должен соответствовать основному домену в настройках приложения и перечисленным значениям в списке доверенных redirect uri — адреса сравниваются до корневого домена

</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">***response\_type*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">Тип ответа, который Вы хотите получить. В большинстве случаев достаточно указать **code**.</td></tr><tr style="height: 144.422px;"><td style="height: 144.422px;">***state*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 144.422px;">**Параметр состояния** в протоколах авторизации позволяет восстановить предыдущее состояние приложения. > Его используют, чтобы защитить приложение от атак, связанных с подделкой запросов (CSRF). Для этого с каждым запросом аутентификации связывают уникальное и не поддающееся угадыванию значение

</td></tr></tbody></table>

<details id="bkmrk-%C2%A0"><summary>Пример запроса</summary>

```json
https://oauth.tpu.ru/authorize?client_id=1&redirect_uri=http://example.com/callback&response_type=code&state=bdc1c79ecb83c00122d24a77e06aa5dc16c8280f7541e89a32108659c353f5
```

</details>#### Ввод корпоративных данных

2\. Если пользователь уже авторизован, то браузер будет перенаправлен по адресу **redirect\_uri**, в противном случае пользователю будет предложено ввести свой корпоративный логин и пароль в системе.

[![Screenshot_71.jpg](https://devdocs.tpu.ru/uploads/images/gallery/2025-07/scaled-1680-/screenshot-71.jpg)](https://devdocs.tpu.ru/uploads/images/gallery/2025-07/screenshot-71.jpg)

3\. Получение токена авторизации **access\_token** для пользователя и для приложения. методом GET передаем параметры на следующий адрес:

<p class="callout info">**[https://oauth.tpu.ru/access\_token](https://oauth.tpu.ru/access_token)**</p>

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-client-1" style="border-collapse: collapse; width: 100%; height: 450.828px;"><colgroup><col style="width: 26.6072%;"></col><col style="width: 73.512%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**Ключ**</td><td style="height: 29.7969px;">**Значение**</td></tr></thead><tbody><tr style="height: 29.7969px;"><td style="height: 29.7969px;">***client\_id*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">Идентификатор приложения</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****client\_secret***** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">Секретный ключ приложения</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****timestamp*****</td><td style="height: 29.7969px;">Текущая метка времени в формате Unix timestamp

Данный параметр обязательный, если требуется произвести обезличенную авторизацию приложения.

</td></tr><tr style="height: 46.5938px;"><td style="height: 46.5938px;">*****code***** <span style="color: rgb(224, 62, 45);">(обязательно, для авторизации пользователей)</span></td><td style="height: 46.5938px;">Одноразовый короткоживущий код, который клиент должен использовать для обмена на токен доступа, полученный на первом этапе авторизации. Данное поле обязательно - если авторизуется пользователь.

</td></tr><tr style="height: 144.422px;"><td style="height: 144.422px;">*****state***** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 144.422px;">**Параметр состояния** в протоколах авторизации позволяет восстановить предыдущее состояние приложения. > Его используют, чтобы защитить приложение от атак, связанных с подделкой запросов (CSRF). Для этого с каждым запросом аутентификации связывают уникальное и не поддающееся угадыванию значение

</td></tr><tr style="height: 110.828px;"><td style="height: 110.828px;">*****grant\_type***** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 110.828px;">Тип авторизации

> разрешённые типы можно посмотреть в настройках авторизации вашего приложения

</td></tr><tr><td>*****redirect\_uri***** *<span style="color: rgb(224, 62, 45);">(обязательно)</span>*

</td><td>должно совпадать с предыдущим запросом

</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****sig***** <span style="color: rgb(224, 62, 45);">(обязательно, для авторизации приложений)</span>

</td><td style="height: 29.7969px;">[Контрольная сумма](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/rascet-kontrolnoi-summy "Расчет контрольной суммы") GET-параметров из HTTP-запроса. Данный параметр обязательный если был пропущен первый шаг получения **code.**

> Ключом для расчета контрольной суммы указывается секретный ключ [доверенного приложения](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/doverennye-prilozeniia-tpu "Доверенные приложения ТПУ"), а **не** ключ версии приложения.

</td></tr></tbody></table>

<details id="bkmrk-%D0%9F%D1%80%D0%B8%D0%BC%D0%B5%D1%80-%D0%B7%D0%B0%D0%BF%D1%80%D0%BE%D1%81%D0%B0-https"><summary>Пример запроса</summary>

```json
https://oauth.tpu.ru/access_token?client_id=1&client_secret=H2PkHm&redirect_uri=http://mysite.ru&code=7a6fa4dff77a228eeda56603b8f53806c883f011c40b72630bb50df056f6479&grant_type=authorization_code
```

</details>В результате выполнения данного запроса Ваш сервер получит вновь созданный **access\_token**. Вместе с **access\_token** серверу возвращается время жизни ключа **expires\_in** в секундах. **expires\_in** по умолчанию 24 часа. Процедуру авторизации приложения необходимо повторять в случае истечения срока действия **access\_token**, смены пользователем своего логина или пароля, или удаления приложения из настроек. Также в ответе присутствует **refresh\_token** - срок годности 1 неделя, с помощью его можно получить новый **access\_token** не проводя процедуру авторизации повторно.

4\. Проверка токена авторизации, данный метод вернёт сведения об указанном токене авторизации

<p class="callout info">**https://oauth.tpu.ru/check-token**</p>

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-client-2" style="border-collapse: collapse; width: 100%; height: 148.984px;"><colgroup><col style="width: 100.03%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**В HTTP заголовке-*Authorization***</td></tr></thead><tbody><tr style="height: 29.7969px;"><td style="height: 29.7969px;">Требуется указать строку следующего формата

> Bearer &lt;Токен авторизации&gt;

</td></tr></tbody></table>

В случае успешной проверки токена, придёт ответ в формате JSON, в котором будет полезная информация, идентификатор клиента, авторизовавшего пользователя (**client\_id**), идентификатор пользователя (**user\_id**), идентификатор личности (**lichnost\_id**), контекст пользователя (**username**), время создания токена, а так же тип токена (**type**). Токены бывают двух типов - личные (**personal**) и системные (**system**).

Персональные токены получены - путём прямой авторизации пользователя в приложении (ввод логина и пароля), системные токены формируются в доверительных приложениях и микро-сервисах с использованием механизма отложенной авторизации.

<details id="bkmrk-%D0%9F%D1%80%D0%B8%D0%BC%D0%B5%D1%80-%D0%BE%D1%82%D0%B2%D0%B5%D1%82%D0%B0-%7B-%C2%A0-%C2%A0-"><summary>Пример ответа</summary>

```json
{
    "message": "Valid",
    "body": {
        "created": "2025-07-21 09:32:26",
        "expired": "2025-07-22 09:32:26",
        "client_id": "my-app-id-123456",
        "type": "personal",
        "user_id": 123,
        "lichnost_id": 123,
        "username": "portal_login"
    }
}
```

</details><div id="bkmrk--2"></div>5\. Получение данных об авторизированном пользователе

<p class="callout info">**https://oauth.tpu.ru/user**</p>

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-client-3" style="border-collapse: collapse; width: 100%; height: 148.984px;"><colgroup><col style="width: 100.03%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**В HTTP заголовке-Authorization**</td></tr></thead><tbody><tr><td>Требуется указать строку следующего формата

> Bearer &lt;Токен авторизации&gt;

</td></tr></tbody></table>

<details id="bkmrk-%D0%9F%D1%80%D0%B8%D0%BC%D0%B5%D1%80-%D0%BE%D1%82%D0%B2%D0%B5%D1%82%D0%B0-%7B-%22use"><summary>Пример ответа</summary>

```json
{
    "user_id": 59568,
    "lichnost_id": 745454,
    "elements": {
        "familiya": "Иванов",
        "imya": "Иван",
        "otchestvo": "Иванович"
    },
    "full_name": "Иванов Иван Иванович",
    "last_name": "Иванов",
    "first_name": "Иван",
    "patronymic": "Иванович",
    "email": "login@tpu.ru",
  
    "login": "login",
    "message": "OK"
}
```

</details>6\. Завершение сессии пользователя. Для успешной завершении сессии пользователя нужно сделать редирект на следующий URL.

<p class="callout info">***https://oauth.tpu.ru/auth/logout?redirect=&lt;redirect\_uri&gt;***</p>

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-redire" style="border-collapse: collapse; width: 100%; height: 148.984px;"><colgroup><col style="width: 26.6072%;"></col><col style="width: 73.512%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**Ключ**</td><td style="height: 29.7969px;">**Значение**</td></tr></thead><tbody><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****redirect\_uri*****</td><td style="height: 29.7969px;">Cсылка, на которую, после успешного завершения сессии, сервер делает редирект пользователя.</td></tr></tbody></table>

> На этой странице вы должны локально завершить сессию пользователя и сделать редирект на главную страницу вашего приложения.

#### Вместо заключения

В вашем приложении вы должны хранить в сессии **идентификатор авторизованного пользователя** и **access\_token**. Для получения других данных о пользователе вы можете воспользоваться доступными методами RESTful API.

Секретные ключи вашего приложения вы не должны хранить на клиенте и обязаны обеспечить надлежащую их сохранность.

# Восстановление авторизации

После авторизации токен обычно существует ограниченное количество времени, для того чтобы продолжить использовать авторизацию пользователя - реализуйте обновление токена для поддержания аутентификации без взаимодействия с пользователем следующим образом:

Для получения нового **access\_token** необходимо выполнить GET-запрос на следующий эндпоинт, передав обязательные параметры:

<p class="callout info">**[https://oauth.tpu.ru/access\_token](https://oauth.tpu.ru/access_token)**</p>

<table border="1" id="bkmrk-%D0%9A%D0%BB%D1%8E%D1%87-%D0%97%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-client-1" style="border-collapse: collapse; width: 100%; height: 450.828px;"><colgroup><col style="width: 26.6072%;"></col><col style="width: 73.512%;"></col></colgroup><thead><tr style="height: 29.7969px;"><td style="height: 29.7969px;">**Ключ**</td><td style="height: 29.7969px;">**Значение**</td></tr></thead><tbody><tr style="height: 29.7969px;"><td style="height: 29.7969px;">***client\_id*** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">[Идентификатор приложения](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/nastroiki-edinoi-avtorizacii "Идентификатор приложения")</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****client\_secret***** <span style="color: rgb(224, 62, 45);">(обязательно)</span></td><td style="height: 29.7969px;">[Секретный ключ приложения](https://devdocs.tpu.ru/books/prilozeniia-tpu/page/nastroiki-edinoi-avtorizacii "Секретный ключ приложения")</td></tr><tr><td>*****grant\_type***** *<span style="color: rgb(224, 62, 45);">(обязательно)</span>*</td><td>Значение должно **refresh\_token**

</td></tr><tr style="height: 29.7969px;"><td style="height: 29.7969px;">*****refresh\_token***** <span style="color: rgb(224, 62, 45);">(обязательно)</span>

</td><td style="height: 29.7969px;">Требуется указать ключ обновления, полученный при авторизации

</td></tr></tbody></table>

<details id="bkmrk-%D0%9F%D1%80%D0%B8%D0%BC%D0%B5%D1%80-%D0%B7%D0%B0%D0%BF%D1%80%D0%BE%D1%81%D0%B0-https"><summary>Пример запроса</summary>

```json
https://oauth.tpu.ru/access_token?client_id=1&client_secret=12345678&grant_type=refresh_token&refresh_token=def502000c9e8750e3a5a39be466d664732e21303a491ef50cea65d2da1e4533ade84dfc1b503ddd282849a14e05996d176f211a8a979d2f47f65565b94da1e9be750e6e78053df0d00168d5b937a54e74e5e7adfa769d8377fd054fd9bee9f49f46dfb4afaefebc5cb582210f225cec21ee5e0fcfcd6808f365df72026f2424c52af750cac6987e5738c14a172094241499bb1fd1131ea20c2dbd72a09e5522940f601895beb9443700f4561766da0344876f821ae9de08832859b35540e679cedad51ad52aa1d0d4c7e5e7b3dcbecab278e3755dfdb94303c4b41b32c7800c9724cf081f71427afc9611b75ae726d1ba5dbb6237bc21c74326212adc83b8d50c440ffd6516847ea6ae643d77b3bd4109113ac250d1ced80a
```

</details>В результате выполнения данного запроса Ваш сервер получит вновь созданный **access\_token**. Вместе с **access\_token** серверу возвращается время жизни ключа **expires\_in** в секундах. Процедуру авторизации приложения необходимо повторять в случае истечения срока действия **access\_token**, смены пользователем своего логина или пароля, или удаления приложения из настроек. Также в ответе присутствует **refresh\_token** - срок годности 1 неделя, с помощью его можно получить новый **access\_token** не проводя процедуру авторизации повторно.

# Вместо заключения



# Полезная информация

#### Ссылки

- [Текущая версия драфта стандарта OAuth 2.0](http://tools.ietf.org/html/draft-ietf-oauth-v2)
- [Официальный сайт OAuth](http://oauth.net/)
- [Рабочая группа по выработке стандарта](https://www.ietf.org/mailman/listinfo/oauth) ([архивы](http://www.ietf.org/mail-archive/web/oauth/))