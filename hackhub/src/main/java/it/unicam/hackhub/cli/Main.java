package it.unicam.hackhub.cli;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        AppContext appContext = new AppContext();
        appContext.init();
        SessionContext sessionContext = appContext.getSessionContext();
        Scanner scanner = new Scanner(System.in);
        CommandRegistry registry = appContext.createCommandRegistry(scanner);

        MainMenu mainMenu = new MainMenu(registry, scanner, sessionContext);
        mainMenu.run();
    }
}
