package com.example.footballdata.service;

import com.example.footballdata.models.EventType;
import com.example.footballdata.models.MatchDetails;
import com.example.footballdata.models.MatchEvent;
import lombok.AllArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ScraperService {

    private static final String URL = "https://www.transfermarkt.de/spielbericht/index/spielbericht/";
    private final ChromeDriver driver;

    @PostConstruct
    void postConstruct() {
        scrapeMatch("3588168");
        driver.quit();
    }

    public void scrapeMatch(final String matchId) {
        driver.get(URL + matchId);

        final String homeTeam = driver.findElementsByClassName("sb-vereinslink").get(0).getText();
        final String awayTeam = driver.findElementsByClassName("sb-vereinslink").get(1).getText();

        MatchDetails matchDetails = getMatchDetails(homeTeam, awayTeam, matchId);
        List<MatchEvent> matchEvents = getMatchGoals(homeTeam, awayTeam);
        List<MatchEvent> matchSubstitutions = getMatchSubstitutions(homeTeam, awayTeam);
        List<MatchEvent> matchCards = getMatchCards(homeTeam, awayTeam);

        System.out.println("\n"+ "- Spieldetails -");
        System.out.println(matchDetails + "\n");

        System.out.println("\n"+ "- Tore -");
        System.out.println(matchEvents + "\n");

        System.out.println("\n" + "- Wechsel -");
        System.out.println(matchSubstitutions + "\n");

        System.out.println("\n" + "- Karten -");
        System.out.println(matchCards + "\n");
    }

    public MatchDetails getMatchDetails(String homeTeam, String awayTeam, String matchId) {
        MatchDetails details = new MatchDetails();
        details.setMatchId(matchId);

        String matchDate = driver.findElementByClassName("sb-datum").getText();

        String matchDay = matchDate.substring(0, 1);
        details.setMatchDay(matchDay);

        String date = matchDate.substring(matchDate.indexOf("|") + 7, matchDate.indexOf("|", matchDate.indexOf("|") + 1) - 2);
        String dateString = "20" + date.substring(6, 8) + "-" + date.substring(3, 5) + "-" + date.substring(0, 2);
        details.setDate(dateString);

        String time = matchDate.substring(matchDate.indexOf(":") - 2, matchDate.indexOf(":") + 3);
        details.setTime(time);

        String halftimeResult = driver.findElementByClassName("sb-endstand").getText().substring(5, 8);
        details.setHalftimeResult(halftimeResult);

        String ftResult = getNodeText(driver.findElementByClassName("sb-endstand"));
        details.setFulltimeResult(ftResult);

        details.setHomeTeam(homeTeam);
        details.setAwayTeam(awayTeam);

        return details;
    }

    public List<MatchEvent> getMatchGoals(String homeTeam, String awayTeam) {
        List<MatchEvent> goalEvents = new ArrayList<>();
        final List<WebElement> goals = driver.findElementById("sb-tore").findElements(By.tagName("li"));

        goals.forEach(goal -> {
            MatchEvent goalEvent = new MatchEvent();
            goalEvent.setType(EventType.GOAL);

            WebElement time = goal.findElement(By.className("sb-sprite-uhr-klein"));
            String timeString = getMinute(time.getCssValue("background-position")) + time.getText().replaceAll(" ", "");
            goalEvent.setMinute(timeString);

            String team = getTeam(goal, homeTeam, awayTeam);
            goalEvent.setTeam(team);

            String result = goal.findElement(By.className("sb-aktion-spielstand")).getText();
            goalEvent.setNewResult(result);

            String action = goal.findElement(By.className("sb-aktion-aktion")).getText();
            String player = action.substring(0, action.indexOf(","));
            goalEvent.setPlayer(player);

            if (action.contains("Vorarbeit:")) {
                int startIndex = action.indexOf(":") + 1;
                int endIndex = action.indexOf(",", action.indexOf(":")) - 1;
                String assist = action.replace("\n", "").substring(startIndex, endIndex);
                goalEvent.setAssistPlayer(assist);
            }

            goalEvents.add(goalEvent);
        });
        return goalEvents;
    }

    public List<MatchEvent> getMatchSubstitutions(String homeTeam, String awayTeam) {
        List<MatchEvent> subEvents = new ArrayList<>();
        final List<WebElement> substitutions = driver.findElementById("sb-wechsel").findElements(By.tagName("li"));
        substitutions.forEach(sub -> {
            MatchEvent subEvent = new MatchEvent();
            subEvent.setType(EventType.SUBSTITUTION);

            String time = getMinute(sub);
            subEvent.setMinute(time);

            String team = getTeam(sub, homeTeam, awayTeam);
            subEvent.setTeam(team);

            String playerIn = sub.findElement(By.className("sb-aktion-wechsel-ein")).getText().substring(2);
            subEvent.setPlayer(playerIn);

            String playerOut = sub.findElement(By.className("sb-aktion-wechsel-aus")).getText();
            String playerOutString = playerOut.substring(0, playerOut.indexOf(","));
            subEvent.setAssistPlayer(playerOutString);

            String reason = playerOut.substring(playerOut.indexOf(",") + 1).replace(" ", "");
            subEvent.setReason(reason);

            subEvents.add(subEvent);
        });
        return subEvents;
    }

    public List<MatchEvent> getMatchCards(String homeTeam, String awayTeam) {
        List<MatchEvent> cardEvents = new ArrayList<>();
        final List<WebElement> cardsContainer = driver.findElementsById("sb-karten");

        if (cardsContainer.size() > 0) {
            final List<WebElement> cards = cardsContainer.get(0).findElements(By.tagName("li"));
            cards.forEach(card -> {
                MatchEvent cardEvent = new MatchEvent();
                cardEvent.setType(EventType.CARD);

                String team = getTeam(card, homeTeam, awayTeam);
                cardEvent.setTeam(team);

                String time = getMinute(card);
                cardEvent.setMinute(time);

                String action = card.findElement(By.className("sb-aktion-aktion")).getText();
                String player = action.substring(0, action.indexOf("\n"));
                cardEvent.setPlayer(player);

                String cardType = action.substring(action.indexOf(".") + 2, action.indexOf(",") - 1);
                cardEvent.setCardType(cardType);

                cardEvents.add(cardEvent);
            });
        }

        return cardEvents;
    }

    private String getNodeText(WebElement element) {
        String text = element.getText();
        for (WebElement child : element.findElements(By.xpath("./*"))) {
            text = text.replace(child.getText(), "").replace("\n", "");
        }
        return text;
    }

    private String getTeam(WebElement element, String homeTeam, String awayTeam) {
        String classes = element.getAttribute("class");
        for (String c : classes.split(" ")) {
            if (c.equals("sb-aktion-heim")) {
                return homeTeam;
            } else if (c.equals("sb-aktion-gast")) {
                return awayTeam;
            }
        }
        return null;
    }

    private String getMinute(WebElement element) {
        WebElement time = element.findElement(By.className("sb-sprite-uhr-klein"));
        return getMinute(time.getCssValue("background-position")) + time.getText().replaceAll(" ", "");
    }

    public String getMinute(String position) {
        switch (position) {
            case "0px 0px":     return "1";
            case "-36px 0px":   return "2";
            case "-72px 0px":   return "3";
            case "-108px 0px":  return "4";
            case "-144px 0px":  return "5";
            case "-180px 0px":  return "6";
            case "-216px 0px":  return "7";
            case "-252px 0px":  return "8";
            case "-288px 0px":  return "9";
            case "-324px 0px":  return "10";

            case "0px -36px":       return "11";
            case "-36px -36px":     return "12";
            case "-72px -36px":     return "13";
            case "-108px -36px":    return "14";
            case "-144px -36px":    return "15";
            case "-180px -36px":    return "16";
            case "-216px -36px":    return "17";
            case "-252px -36px":    return "18";
            case "-288px -36px":    return "19";
            case "-324px -36px":    return "20";

            case "0px -72px":       return "21";
            case "-36px -72px":     return "22";
            case "-72px -72px":     return "23";
            case "-108px -72px":    return "24";
            case "-144px -72px":    return "25";
            case "-180px -72px":    return "26";
            case "-216px -72px":    return "27";
            case "-252px -72px":    return "28";
            case "-288px -72px":    return "29";
            case "-324px -72px":    return "30";

            case "0px -108px":      return "31";
            case "-36px -108px":    return "32";
            case "-72px -108px":    return "33";
            case "-108px -108px":   return "34";
            case "-144px -108px":   return "35";
            case "-180px -108px":   return "36";
            case "-216px -108px":   return "37";
            case "-252px -108px":   return "38";
            case "-288px -108px":   return "39";
            case "-324px -108px":   return "40";

            case "0px -144px":      return "41";
            case "-36px -144px":    return "42";
            case "-72px -144px":    return "43";
            case "-108px -144px":   return "44";
            case "-144px -144px":   return "45";
            case "-180px -144px":   return "46";
            case "-216px -144px":   return "47";
            case "-252px -144px":   return "48";
            case "-288px -144px":   return "49";
            case "-324px -144px":   return "50";

            case "0px -180px":      return "51";
            case "-36px -180px":    return "52";
            case "-72px -180px":    return "53";
            case "-108px -180px":   return "54";
            case "-144px -180px":   return "55";
            case "-180px -180px":   return "56";
            case "-216px -180px":   return "57";
            case "-252px -180px":   return "58";
            case "-288px -180px":   return "59";
            case "-324px -180px":   return "60";

            case "0px -216px":      return "61";
            case "-36px -216px":    return "62";
            case "-72px -216px":    return "63";
            case "-108px -216px":   return "64";
            case "-144px -216px":   return "65";
            case "-180px -216px":   return "66";
            case "-216px -216px":   return "67";
            case "-252px -216px":   return "68";
            case "-288px -216px":   return "69";
            case "-324px -216px":   return "70";

            case "0px -252px":      return "71";
            case "-36px -252px":    return "72";
            case "-72px -252px":    return "73";
            case "-108px -252px":   return "74";
            case "-144px -252px":   return "75";
            case "-180px -252px":   return "76";
            case "-216px -252px":   return "77";
            case "-252px -252px":   return "78";
            case "-288px -252px":   return "79";
            case "-324px -252px":   return "80";

            case "0px -288px":      return "81";
            case "-36px -288px":    return "82";
            case "-72px -288px":    return "83";
            case "-108px -288px":   return "84";
            case "-144px -288px":   return "85";
            case "-180px -288px":   return "86";
            case "-216px -288px":   return "87";
            case "-252px -288px":   return "88";
            case "-288px -288px":   return "89";
            case "-324px -288px":   return "90";

            case "0px -324px":      return "91";
            case "-36px -324px":    return "92";
            case "-72px -324px":    return "93";
            case "-108px -324px":   return "94";
            case "-144px -324px":   return "95";
            case "-180px -324px":   return "96";
            case "-216px -324px":   return "97";
            case "-252px -324px":   return "98";
            case "-288px -324px":   return "99";
            case "-324px -324px":   return "100";

            case "0px -360px":      return "101";
            case "-36px -360px":    return "102";
            case "-72px -360px":    return "103";
            case "-108px -360px":   return "104";
            case "-144px -360px":   return "105";
            case "-180px -360px":   return "106";
            case "-216px -360px":   return "107";
            case "-252px -360px":   return "108";
            case "-288px -360px":   return "109";
            case "-324px -360px":   return "110";

            case "0px -396px":      return "111";
            case "-36px -396px":    return "112";
            case "-72px -396px":    return "113";
            case "-108px -396px":   return "114";
            case "-144px -396px":   return "115";
            case "-180px -396px":   return "116";
            case "-216px -396px":   return "117";
            case "-252px -396px":   return "118";
            case "-288px -396px":   return "119";
            case "-324px -396px":   return "120";

            case "0px -432px": return "-";

            default:
                return "-1";
        }
    }

}
