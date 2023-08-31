package org.pikalovanna.congratulationsbot.controller;

import org.pikalovanna.congratulationsbot.CongratulationsBot;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
public class BotController {
    private final CongratulationsBot congratulationsBot;

    public BotController(CongratulationsBot congratulationsBot){
        this.congratulationsBot = congratulationsBot;
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public void onUpdateReceived(@RequestBody Update update){
        congratulationsBot.onUpdateReceived(update);
    }
}
