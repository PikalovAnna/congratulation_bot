package org.pikalovanna.congratulationsbot;

import lombok.extern.log4j.Log4j2;
import org.pikalovanna.congratulationsbot.config.TransactionalTemplate;
import org.pikalovanna.congratulationsbot.entity.Congratulation;
import org.pikalovanna.congratulationsbot.entity.User;
import org.pikalovanna.congratulationsbot.enums.ActionStatus;
import org.pikalovanna.congratulationsbot.repository.CongratulationRepository;
import org.pikalovanna.congratulationsbot.repository.UsersRepository;
import org.pikalovanna.congratulationsbot.service.CongratulationService;
import org.pikalovanna.congratulationsbot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedSticker;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Log4j2
public class CongratulationsBot extends TelegramLongPollingBot {

    @Value("${bot.botUsername}")
    private String botUsername;
    @Value("${bot.botToken}")
    private String botToken;

    private final UserService userService;
    private final UsersRepository usersRepository;
    private final CongratulationService congratulationService;
    private final CongratulationRepository congratulationRepository;
    private final TransactionalTemplate transactionalTemplate;

    public CongratulationsBot(UserService userService,
                              UsersRepository usersRepository,
                              CongratulationService congratulationService,
                              CongratulationRepository congratulationRepository,
                              TransactionalTemplate transactionalTemplate) {
        this.userService = userService;
        this.usersRepository = usersRepository;
        this.congratulationService = congratulationService;
        this.congratulationRepository = congratulationRepository;
        this.transactionalTemplate = transactionalTemplate;
    }

    private String helpText(String botName) {
        return "Я могу хранить Ваши поздравления и отправлять\n" +
                "их в чат с именинником или в группу в указанное время.\n " +
                "Для использования inline режима:\n" +
                "- Создайте несколько поздравлений. Это могут быть текст,фото или стикер;\n" +
                "- Войдите в чат с именником и начните писать мое имя\n" +
                "@" + botName + "\n" +
                "- Используйте один из предлагаемых запросов:\n" +
                "1. /text, text или текст: текстовые поздравления\n" +
                "2. /sticker, sticker или стикер: стикеры\n" +
                "3. /photo, photo или фото: сохраненные фотографии\n" +
                "В личном чате вы можете управлять мной при помощи кнопок и команд:\n" +
                "/start - Для начала общения с ботом\n" +
                "/stop - Удалить все поздравления и учетную запись\n" +
                "/help - Информация о доступных командах";
    }

    /**
     * Принимает обновление и направляет его в нужный хендлер, реагирует на команду /start,/stop,/help
     * и ввод данных для сохранения/редактирования
     */
    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasInlineQuery()) {
            inlineQueryHandler(update.getInlineQuery());
        } else if (update.hasCallbackQuery()) {
            CallbackQuery context = update.getCallbackQuery();
            transactionalTemplate.invoke(status -> callbackHandle(context));
        } else if (update.getMessage().getChat().isGroupChat()) {
            forwardSave(update.getMessage());
        } else if (update.hasMessage() && update.getMessage().getChat().isUserChat()) {
            User user = usersRepository.findByUsername(update.getMessage().getChat().getUserName());
            Message message = update.getMessage();
            String messageId = message.getMessageId().toString();
            Long chatId = message.getChatId();
            if (message.hasText()) {
                switch (message.getText()) {
                    case ("/start"): {
                        if (user == null) {
                            saveUser(message.getChat());
                            String greetingText = "Добрый день, я ваш виртуальный помощник " +
                                    "по хранению и распространению поздравлений!\n" + helpText(getBotUsername());
                            InlineKeyboardMarkup createMarkup = createKeyboard("Создать поздравление", "/create");
                            sendTextMessage(message.getChatId(), greetingText, createMarkup);
                        } else {
                            InlineKeyboardMarkup helpMarkup = createMainKeyboard();
                            sendTextMessage(message.getChatId(), "Чем могу Вам помочь?\n/help", helpMarkup);
                        }
                        break;
                    }
                    case ("/help"): {
                        sendTextMessage(message.getChatId(), helpText(getBotUsername()));
                        break;
                    }
                    case ("/stop"): {
                        if (user != null) {
                            congratulationService.deleteAllByUserId(user.getId());
                            userService.deleteUser(user);
                        }
                        break;
                    }
                    case ("/text"):
                    case ("/photo"):
                    case ("/sticker"): {
                        sendTextMessage(message.getChatId(), "Это запрос inline режима. Пример правильного использования в любом чате:\n@" + getBotUsername() + " " + message.getText());
                        break;
                    }
                    default: {
                        if (user != null) {
                            switch (user.getActionStatus()) {
                                case CREATING: {
                                    Congratulation newCongratulation = saveCongratulation(message, user);
                                    InlineKeyboardMarkup markup = createMenuKeyboard(messageId, newCongratulation);
                                    sendTextMessage(chatId, info(newCongratulation), markup);
                                    break;
                                }
                                case ADD_TEXT: {
                                    Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                                    InlineKeyboardMarkup markup = createMenuKeyboard(messageId, congratulation);
                                    congratulation.setText(message.getText());
                                    congratulationRepository.save(congratulation);
                                    sendTextMessage(message.getChatId(), info(congratulation), markup);
                                    break;
                                }
                                case ADD_SEND_DATE: {
                                    Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                                    LocalDateTime dateTime = LocalDateTime.parse(message.getText(), formatter);
                                    if (dateTime.isBefore(LocalDateTime.now())) {
                                        sendSimpleMessage(message.getChatId(), "Пожалуйста, укажите дату и время не ранее текущей");
                                    } else {
                                        congratulation.setDateSend(LocalDateTime.parse(message.getText(), formatter));
                                        congratulationRepository.save(congratulation);
                                        InlineKeyboardMarkup markup = createMenuKeyboard(messageId, congratulation);
                                        sendTextMessage(message.getChatId(), info(congratulation), markup);
                                    }
                                    break;
                                }
                                case ADD_RECEIVER: {
                                    Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                                    congratulation.setReceiver(message.getText());
                                    congratulationRepository.save(congratulation);
                                    InlineKeyboardMarkup markup = createMenuKeyboard(messageId, congratulation);
                                    sendTextMessage(message.getChatId(), info(congratulation), markup);
                                    break;
                                }
                                default: {
                                    sendTextMessage(message.getChatId(), "Ваша команда не определена.\n/help");
                                }
                            }
                        }
                    }
                }
            } else if (user != null) {
                switch (user.getActionStatus()) {
                    case CREATING: {
                        if (message.hasSticker()) {
                            Congratulation newCongratulation = saveCongratulation(message, user);
                            InlineKeyboardMarkup markup = createMenuKeyboard(messageId, newCongratulation);
                            sendTextMessage(message.getChatId(), info(newCongratulation), markup);
                        } else if (message.hasPhoto()) {
                            Congratulation newCongratulation = saveCongratulation(message, user);
                            InlineKeyboardMarkup markup = createMenuKeyboard(messageId, newCongratulation);
                            sendTextMessage(message.getChatId(), info(newCongratulation), markup);
                        }
                        break;
                    }
                    case ADD_STICKER: {
                        Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                        congratulation.setStickerId(message.getSticker().getFileId());
                        congratulationRepository.save(congratulation);
                        InlineKeyboardMarkup markup = createMenuKeyboard(messageId, congratulation);
                        sendTextMessage(message.getChatId(), info(congratulation), markup);
                        break;
                    }
                    case ADD_PHOTO: {
                        Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                        congratulation.setPhotoId(message.getPhoto().get(0).getFileId());
                        congratulationRepository.save(congratulation);
                        InlineKeyboardMarkup markup = createMenuKeyboard(messageId, congratulation);
                        sendTextMessage(message.getChatId(), info(congratulation), markup);
                        break;
                    }
                    default: {
                        sendTextMessage(message.getChatId(), "Ваша команда не определена.\n/help");
                    }
                }
            }
        }
    }


    public void forwardSave(Message message) {
        User user = usersRepository.findByUsername(message.getFrom().getUserName());
        if (user != null && user.getActionStatus() == ActionStatus.ADD_FORWARD) {
            Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
            congratulation.setForward(message.getChatId());
            congratulationRepository.save(congratulation);
            InlineKeyboardMarkup markup = createMenuKeyboard(message.getMessageId().toString(), congratulation);
            sendTextMessage(user.getChatId(), info(congratulation), markup);
            user.setActionStatus(ActionStatus.NO_ACTION);
            usersRepository.save(user);
        }
    }

    /**
     * Обрабатывает входящий запрос (режим встроенного бота)
     */
    public void inlineQueryHandler(InlineQuery inlineQuery) {
        User user = usersRepository.findByUsername(inlineQuery.getFrom().getUserName());
        if (inlineQuery.hasQuery()) {
            List<InlineQueryResult> inlineQueryResults = new ArrayList<>();
            if (user != null) {
                List<Congratulation> congratulations = congratulationService.getAll(user.getUsername());
                if (congratulations.isEmpty()) {
                    inlineQueryResults.add(new InlineQueryResultArticle()
                            .setTitle("Поздравления не найдены")
                            .setDescription("Пожалуйста, добавьте поздравления в личном чате с ботом")
                            .setInputMessageContent(new InputTextMessageContent().setMessageText("..."))
                            .setId("999966"));
                } else {
                    String query = inlineQuery.getQuery().toLowerCase();
                    if (query.contains("/text") || ("text").contains(query) || query.contains("текст") ||
                            ("текст").contains(query)) {
                        for (Congratulation congratulation : congratulations) {
                            if (congratulationService.hasText(congratulation)) {
                                int length = congratulation.getText().length();
                                if (length > 30) {
                                    length = 30;
                                }
                                inlineQueryResults.add(new InlineQueryResultArticle()
                                        .setTitle("Поздравление #" + congratulation.getId())
                                        .setDescription(congratulation.getText().substring(0, length).concat("..."))
                                        .setInputMessageContent(new InputTextMessageContent()
                                                .setMessageText(congratulation.getText()))
                                        .setId(congratulation.getId().toString()));
                            }
                        }
                    } else if (query.contains("/sticker") || ("sticker").contains(query) || query.contains("стикер") ||
                            ("стикер").contains(query)) {
                        for (Congratulation congratulation : congratulations) {
                            if (congratulationService.hasSticker(congratulation)) {
                                inlineQueryResults.add(new InlineQueryResultCachedSticker()
                                        .setStickerFileId(congratulation.getStickerId())
                                        .setId(congratulation.getId().toString()));
                            }
                        }
                    } else if (query.contains("/photo") || ("photo").contains(query) || query.contains("фот") ||
                            ("фотография").contains(query)) {
                        for (Congratulation congratulation : congratulations) {
                            if (congratulationService.hasPhoto(congratulation)) {
                                inlineQueryResults.add(new InlineQueryResultCachedPhoto()
                                        .setPhotoFileId(congratulation.getPhotoId())
                                        .setId(congratulation.getId().toString()));
                            }
                        }
                    }
                }
            }
            if (inlineQueryResults.isEmpty()) {
                inlineQueryResults.add(new InlineQueryResultArticle()
                        .setTitle("По Вашему запросу \"" + inlineQuery.getQuery() + "\" ничего не найдено")
                        .setDescription("Возможно у Вас просто не сохранены поздравления с таким типом или запрос неверный." +
                                " Запросы: sticker, photo, text")
                        .setInputMessageContent(new InputTextMessageContent().setMessageText("..."))
                        .setId("999967"));
            }
            AnswerInlineQuery answerInlineQuery = new AnswerInlineQuery();
            answerInlineQuery.setResults(inlineQueryResults);
            answerInlineQuery.setInlineQueryId(inlineQuery.getId());
            answerInlineQuery.setCacheTime(10);
            try {
                execute(answerInlineQuery);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке ответа на встроенный запрос", e.getCause());
            }
        }
    }

    /**
     * Обрабатывает нажатия кнопок
     */
    public void callbackHandle(CallbackQuery callbackQuery) {
        if (callbackQuery.getData() != null) {

            Message message = callbackQuery.getMessage();
            User user = usersRepository.findByUsername(message.getChat().getUserName());
            Map<String, Object> btnContext = fromCallbackString(callbackQuery.getData());

            String command = callbackQuery.getData();
            String pageNumber = "0";
            String editMessageId = btnContext.get("MESSAGE_KEY") == null ? String.valueOf(message.getMessageId()) :
                    (String) btnContext.get("MESSAGE_KEY");
            if (!btnContext.isEmpty()) {
                command = (String) btnContext.get("BUTTON_KEY");
                pageNumber = btnContext.get("PAGE_KEY") == null ? "0" : (String) btnContext.get("PAGE_KEY");
            }
            ActionStatus status = ActionStatus.NO_ACTION;
            switch (command) {
                case "/create": {
                    List<Congratulation> congratulations = congratulationService.getAll(user.getUsername());
                    String createText = "Пришлите мне поздравление.\nЭто может быть текст,фото или стикер";
                    status = ActionStatus.CREATING;
                    if (congratulations.isEmpty()) {
                        sendTextMessage(message.getChatId(), createText);
                    } else {
                        InlineKeyboardMarkup markup = createKeyboard("Просмотреть существующие",
                                toCallbackString("/list", "linkStart", editMessageId));
                        sendTextMessage(message.getChatId(), createText, markup);
                    }
                    break;
                }
                case "/addText": {
                    status = ActionStatus.ADD_TEXT;
                    sendSimpleMessage(message.getChatId(), "Пришлите текст для поздравления №" + pageNumber);
                    break;
                }
                case "/addSticker": {
                    status = ActionStatus.ADD_STICKER;
                    sendSimpleMessage(message.getChatId(), "Пришлите стикер для поздравления №" + pageNumber);
                    break;
                }
                case "/addPhoto": {
                    status = ActionStatus.ADD_PHOTO;
                    sendSimpleMessage(message.getChatId(), "Пришлите фото для поздравления №" + pageNumber + "\n(Используйте опцию \"Сжать изображение\")");
                    break;
                }
                case "/addDateSend": {
                    status = ActionStatus.ADD_SEND_DATE;
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    sendSimpleMessage(message.getChatId(), "Пришлите дату для поздравления №" + pageNumber +
                            " в формате год-месяц-число часы:минуты.\nНапример: " + now.format(formatter));
                    break;
                }
                case "/addForward": {
                    status = ActionStatus.ADD_FORWARD;
                    sendSimpleMessage(message.getChatId(), "Пришлите сообщение в группу, " +
                            "в которую будет направлено поздравление.\nВнимание: бот должен состоять в этой группе и иметь право на отправку сообщений.");
                    break;
                }
                case "/addReceiver": {
                    status = ActionStatus.ADD_RECEIVER;
                    sendSimpleMessage(message.getChatId(), "Пришлите юзернейм получателя.\nПример: @" + user.getUsername());
                    break;
                }
                case "/update": {
                    if (congratulationRepository.existsById(Long.parseLong(pageNumber))) {
                        status = ActionStatus.UPDATING;
                        Congratulation congratulation = congratulationRepository.getOne(Long.parseLong(pageNumber));
                        congratulation.setStatus(status);
                        congratulationRepository.save(congratulation);
                        InlineKeyboardMarkup markup = createMenuKeyboard(message.getMessageId().toString(), congratulation);
                        sendTextMessage(message.getChatId(), info(congratulation), markup);
                    } else {
                        sendTextMessage(message.getChatId(), "Поздравление не найдено");
                    }
                    break;
                }
                case "/delete": {
                    if (congratulationRepository.existsById(Long.parseLong(pageNumber))) {
                        Congratulation congratulation = congratulationRepository.getOne(Long.parseLong(pageNumber));
                        sendTextMessage(message.getChatId(), "Поздравление удалено");
                        congratulationRepository.delete(congratulation);
                        deleteMessage(message);
                    } else {
                        sendTextMessage(message.getChatId(), "Поздравление не найдено");
                    }
                    break;
                }
                case "/view": {
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    List<InlineKeyboardButton> rowBack = new ArrayList<>();
                    rowBack.add(new InlineKeyboardButton()
                            .setText("Назад")
                            .setCallbackData(toCallbackString("/list",
                                    "linkStart", message.getMessageId().toString())));
                    rows.add(rowBack);
                    markup.setKeyboard(rows);
                    if (congratulationRepository.existsById(Long.parseLong(pageNumber))) {
                        Congratulation congratulation = congratulationRepository.getOne(Long.parseLong(pageNumber));
                        String receiver = congratulation.getReceiver() != null ? congratulation.getReceiver() + "\n" : "";
                        if (congratulation.getText() != null) {
                            sendSimpleMessage(message.getChatId(), receiver + congratulation.getText());
                        }
                        if (congratulation.getPhotoId() != null) {
                            sendPhotoMessage(message.getChatId(), congratulation.getPhotoId(), null);
                        }
                        if (congratulation.getStickerId() != null) {
                            sendStickerMessage(message.getChatId(), congratulation.getStickerId(), null);
                        }
                        sendTextMessage(message.getChatId(), "Данное сообщение для возврата назад к списку", markup);
                    } else {
                        sendTextMessage(message.getChatId(), "Поздравление не найдено", markup);
                    }
                    break;
                }
                case "/list": {
                    Congratulation congratulation = congratulationService.getLast(user.getUsername());
                    Page<Congratulation> page;
                    // Если у пользователя нет сохраненных поздравлений
                    if (congratulation == null) {
                        InlineKeyboardMarkup markup = createKeyboard("Создать",
                                toCallbackString("/create", "0"));
                        sendTextMessage(message.getChatId(), "Поздравления не найдены, создайте новые:)", markup);
                    } else {
                        //Если в качестве страницы пришло linkStart, то берем первую страницу с одной записью
                        if (pageNumber.equals("linkStart")) {
                            page = congratulationService.getPages(user.getUsername(),
                                    PageRequest.of(0, 1));
                            congratulation = page.getContent().get(0);
                            InlineKeyboardMarkup markup = createPageKeyboard(page, editMessageId);
                            sendTextMessage(message.getChatId(), info(congratulation), markup);
                        } else {
                            page = congratulationService.getPages(user.getUsername(),
                                    PageRequest.of(Integer.parseInt(pageNumber), 1));
                            congratulation = page.getContent().get(0);
                            InlineKeyboardMarkup markup = createPageKeyboard(page, editMessageId);
                            editMessage(congratulation, message, markup);
                        }
                    }
                    break;
                }
            }
            if (status == ActionStatus.NO_ACTION || status == ActionStatus.CREATING) {
                Congratulation congratulation = congratulationRepository.findByStatusAndUserUsername(ActionStatus.UPDATING, user.getUsername());
                if (congratulation != null) {
                    congratulation.setStatus(ActionStatus.NO_ACTION);
                    congratulationRepository.save(congratulation);
                }
            }
            user.setActionStatus(status);
            usersRepository.save(user);
        }
    }

    public String info(Congratulation congratulation) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dateSend = congratulation.getDateSend() != null ? congratulation.getDateSend().format(formatter) : "❎";
        String sticker = congratulation.getStickerId() != null ? "✅" : "❎";
        String text = congratulation.getText() != null ? "\n" + congratulation.getText() : "❎";
        String photo = congratulation.getPhotoId() != null ? "✅" : "❎";
        String group = congratulation.getForward() != null ? congratulation.getForward().toString() : "❎";
        String receiver = congratulation.getReceiver() != null ? congratulation.getReceiver() : "❎";
        return "\uD83C\uDF81#" + congratulation.getId() + "\n" +
                "Текст: " + text + "\n" +
                "Стикер: " + sticker + "\n" +
                "Фото: " + photo + "\n" +
                "Дата отправки: " + dateSend + "\n" +
                "Дата создания: " + congratulation.getDateCreate().format(formatter) + "\n" +
                "Группа: " + group + "\n" +
                "Получатель: " + receiver;
    }

    public void editMessage(Congratulation congratulation, Message message, InlineKeyboardMarkup markup) {
        EditMessageText editMessageText = new EditMessageText();
        if (congratulation.getId() != null) {
            editMessageText.setText(info(congratulation));
        }
        editMessageText.setReplyMarkup(markup);
        editMessageText.setMessageId(message.getMessageId());
        editMessageText.setChatId(message.getChatId());
        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error("Произошла ошибка во время редактирования сообщения", e.getCause());
        }
    }

    /**
     * Отправляет стикер с кнопками в чат
     */
    public void sendStickerMessage(Long chatId, String stickerId, InlineKeyboardMarkup keyboard) {
        SendSticker sendSticker = new SendSticker()
                .setChatId(chatId)
                .setSticker(stickerId);
        if (keyboard != null)
            sendSticker.setReplyMarkup(keyboard);
        try {
            execute(sendSticker);
        } catch (TelegramApiException e) {
            log.error("Произошла ошибка во время отправки стикера", e.getCause());
        }
    }

    /**
     * Отправляет фото в чат
     */
    public void sendPhotoMessage(Long chatId, String photoId, InlineKeyboardMarkup keyboardMarkup) {
        SendPhoto sendPhoto = new SendPhoto()
                .setPhoto(photoId)
                .setChatId(chatId);
        if (keyboardMarkup != null)
            sendPhoto.setReplyMarkup(keyboardMarkup);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Возникла ошибка при отправке фото", e.getCause());
        }
    }

    /**
     * Удаляет сообщение по id чата и id сообщения
     */
    private void deleteMessage(Message msg) {
        DeleteMessage deleteMessage = new DeleteMessage()
                .setMessageId(msg.getMessageId())
                .setChatId(msg.getChatId());
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Произошла ошибка во время удаления сообщения", e.getCause());
        }
    }

    /**
     * Записывает в callback_data идентификатор кнопки и идентификатор сообщения в котором хранится контекст кнопки
     */
    static String toCallbackString(String button, String currentPage, String messageId) {
        String data = "BUTTON_KEY=" + button + ";PAGE_KEY=" + currentPage + ";";
        if (!messageId.equals(""))
            data += "MESSAGE_KEY=" + messageId + ";";
        if (data.length() > 64)
            throw new IllegalArgumentException("Длина callback_data превышает 64 символа");
        return data;
    }

    /**
     * Записывает в callback_data идентификатор кнопки и идентификатор сообщения в котором хранится контекст кнопки
     */
    static String toCallbackString(String button, String currentPage) {
        return toCallbackString(button, currentPage, "");
    }

    /**
     * Преобразует callback_data в карту
     */
    static Map<String, Object> fromCallbackString(String data) {
        Map<String, Object> context = new HashMap<>();
        String[] commands = data.split(";");
        if (commands.length > 1)
            for (String command : commands) {
                context.put(command.split("=")[0], command.split("=")[1]);
            }
        return context;
    }


    /**
     * Создает разметку с одной кнопкой
     */
    public InlineKeyboardMarkup createKeyboard(String text, String callback) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(new InlineKeyboardButton().setText(text)
                .setCallbackData(callback));
        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    /**
     * Создает разметку с кнопками по умолчанию
     */
    public InlineKeyboardMarkup createMainKeyboard() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> rowList = new ArrayList<>();
        List<InlineKeyboardButton> rowCreate = new ArrayList<>();
        rowList.add(new InlineKeyboardButton().setText("\uD83D\uDD0D Просмотреть все")
                .setCallbackData(toCallbackString("/list", "linkStart")));
        rowCreate.add(new InlineKeyboardButton().setText("Создать")
                .setCallbackData(toCallbackString("/create", "0")));
        rows.add(rowCreate);
        rows.add(rowList);
        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    /**
     * Создает кнопки стандартного меню
     */
    public InlineKeyboardMarkup createMenuKeyboard(String messageId, Congratulation congratulation) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (congratulation != null) {
            String congratulationId = congratulation.getId().toString();

            List<InlineKeyboardButton> addRow = new ArrayList<>();
            addRow.add(new InlineKeyboardButton().setText("➕ Текст")
                    .setCallbackData(toCallbackString("/addText", congratulationId, messageId)));
            addRow.add(new InlineKeyboardButton().setText("➕ Стикер")
                    .setCallbackData(toCallbackString("/addSticker", congratulationId, messageId)));
            addRow.add(new InlineKeyboardButton().setText("➕ Фото")
                    .setCallbackData(toCallbackString("/addPhoto", congratulationId, messageId)));
            rows.add(addRow);


            List<InlineKeyboardButton> groupRow = new ArrayList<>();
            groupRow.add(new InlineKeyboardButton().setText("➕ Дата отправки")
                    .setCallbackData(toCallbackString("/addDateSend", congratulationId, messageId)));
            groupRow.add(new InlineKeyboardButton().setText("➕ Группа")
                    .setCallbackData(toCallbackString("/addForward", congratulationId, messageId)));
            groupRow.add(new InlineKeyboardButton().setText("➕ Получатель")
                    .setCallbackData(toCallbackString("/addReceiver", congratulationId, messageId)));
            rows.add(groupRow);


            List<InlineKeyboardButton> viewAllRow = new ArrayList<>();
            viewAllRow.add(new InlineKeyboardButton().setText("Просмотр поздравления")
                    .setCallbackData(toCallbackString("/view", congratulationId, messageId)));
            viewAllRow.add(new InlineKeyboardButton().setText("\uD83D\uDD0D Просмотреть все")
                    .setCallbackData(toCallbackString("/list", "linkStart", messageId)));
            rows.add(viewAllRow);
        }

        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    /**
     * Создает разметку с кнопками для постраничного пролистывания и добавляет createMenuKeyboard
     */
    public InlineKeyboardMarkup createPageKeyboard(Page<Congratulation> page, String messageId) {

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> paginationRow = new ArrayList<>();

        if (page.getNumber() - 1 >= 0) {
            paginationRow.add(new InlineKeyboardButton()
                    .setText("◀")
                    .setCallbackData(toCallbackString("/list", Integer.toString(page.getNumber() - 1), messageId)));
        }
        if (page.getNumber() + 1 < page.getTotalPages()) {
            paginationRow.add(new InlineKeyboardButton()
                    .setText("▶")
                    .setCallbackData(toCallbackString("/list", Integer.toString(page.getNumber() + 1), messageId)));
        }
        rows.add(paginationRow);

        List<InlineKeyboardButton> updateRow = new ArrayList<>();
        updateRow.add(new InlineKeyboardButton()
                .setText("\uD83D\uDCDD Редактировать")
                .setCallbackData(toCallbackString("/update",
                        page.getContent().get(0).getId().toString(),
                        messageId)));
        updateRow.add(new InlineKeyboardButton()
                .setText("❌ Удалить")
                .setCallbackData(toCallbackString("/delete",
                        page.getContent().get(0).getId().toString(),
                        messageId)));
        rows.add(updateRow);


        List<InlineKeyboardButton> createRow = new ArrayList<>();
        createRow.add(new InlineKeyboardButton()
                .setText("Создать")
                .setCallbackData(toCallbackString("/create",
                        page.getContent().get(0).getId().toString(),
                        messageId)));
        rows.add(createRow);

        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    /**
     * Сохраняет пользователя
     */
    public void saveUser(Chat chat) {
        User newUser = new User();
        newUser.setLastName(chat.getLastName());
        newUser.setFirstName(chat.getFirstName());
        newUser.setUsername(chat.getUserName());
        newUser.setChatId(chat.getId());
        userService.saveUser(newUser);
    }

    /**
     * Сохраняет поздравление
     */
    public Congratulation saveCongratulation(Message message, User user) {
        Congratulation congratulation = new Congratulation();
        congratulation.setDateCreate(LocalDateTime.now());
        if (message.hasSticker()) {
            congratulation.setStickerId(message.getSticker().getFileId());
        }
        if (message.hasText()) {
            congratulation.setText(message.getText());
        }
        if (message.hasPhoto()) {
            congratulation.setPhotoId(message.getPhoto().get(0).getFileId());
        }
        congratulation.setStatus(ActionStatus.UPDATING);
        congratulation.setUser(user);
        return congratulationService.saveCongratulation(congratulation);
    }

    /**
     * Отправляет текстовое сообщение с кнопками по умолчанию
     */
    public void sendTextMessage(Long chatId, String text) {
        sendTextMessage(chatId, text, null);
    }

    /**
     * Отправляет текстовое сообщение без кнопок
     */
    public void sendSimpleMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(chatId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Произошла ошибка при отправке сообщения", e.getCause());
        }
    }

    /**
     * Отправляет текстовое сообщение + разметка с кнопками
     */
    public void sendTextMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {

        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(chatId);

        if (keyboard != null)
            sendMessage.setReplyMarkup(keyboard);
        else
            sendMessage.setReplyMarkup(createMainKeyboard());

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Произошла ошибка при отправке сообщения", e.getCause());
        }
    }

    /**
     * Поочередность обработки входящих обновлений
     */
    @Override
    public void onUpdatesReceived(List<Update> updates) {
        updates.forEach(this::onUpdateReceived);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

}
