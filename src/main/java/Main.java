import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {

    private static ArrayList<CarOLX> listOlx = new ArrayList<>();
    private static ArrayList<String> linksToOlxAds = new ArrayList<>();
    private static WebDriver driver;

    private static boolean shouldParsePhone;

    private static String brand,          // марка
                          model,          // модель
                          image,          // ссылка на изображение
                          year,           // год выпуска
                          price,          // цена в грн
                          color,          // цвет
                          capacity,       // объем двигателя
                          mileage,        // пробег (тыс. км)
                          bodyType,       // тип кузова
                          fuel,           // тип топлива
                          gearBox,        // тип коробки
                          city,           // город
                          phone,          // номер телефон
                          datePublicated, // дата публикации объявления
                          postIdOLX;      // номер объявления OLX

    public static void main(String[] args) throws Exception {

        boolean c = true; // флаг для цикла ввода
        Scanner readUserInput = new Scanner(System.in);

        while (c) {
            System.out.println("Парсить номера телефонов? (y/n)");
            char x = readUserInput.next().charAt(0); // считываем из консоли
            switch (x) {
                case 'y': {
                    shouldParsePhone = true;
                    c = false;
                    break;
                }
                case 'n': {
                    shouldParsePhone = false;
                    c = false;
                    break;
                }
                default: {
                    System.out.println("Ошибка ввода");
                }
            }
        }
        System.out.println("shouldParsePhone " + shouldParsePhone);
        parseOLX();

    }

    public static void parseOLX() throws Exception {

        for (int i = 0; i < 500; i++) {
            Response response = throwRequestToOLX(i+1);    // получаем i-ю страницу
            Document doc = Jsoup.parse(response.body().string());    // парсим ответ в HTML
            Elements elements = doc.select("tbody");        // берём элемент tbody
            Elements rawLinks = elements.select("a[href]");          // а в нём - ссылки
            for (Element element: rawLinks) {
                String tempStr = element.attr("abs:href");
                if(tempStr.contains("obyavlenie") && !tempStr.equals("")) {
                    tempStr = tempStr.replace(";promoted", "");
                    if(!linksToOlxAds.contains(tempStr)) {
                        linksToOlxAds.add(tempStr);
                    }
                }
            }
        }
        for(int i = 0; i < linksToOlxAds.size(); i++) {
            parseOlxAd(linksToOlxAds.get(i)); // парсим полученные ссылки
        }
    }

    public static Response throwRequestToOLX(int pageIndex) throws Exception {

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW");
        RequestBody body = RequestBody.create(mediaType, "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"page\"\r\n\r\n"
                + pageIndex +
                "\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"search[category_id]\"\r\n\r\n108\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--");
        Request request = new Request.Builder()
                .url("https://www.olx.ua/ajax/search/list/")
                .post(body)
                .addHeader("content-type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Postman-Token", "e80210aa-1766-41e1-b6a4-730581e1720b")
                .build();

        Response response = client.newCall(request).execute();
        return  response;
    }

    public static void parseOlxAd(String url) throws Exception {

        boolean isAdActive = true;

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW");
        RequestBody body = RequestBody.create(mediaType, "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"page\"\r\n\r\n2\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"search[category_id]\"\r\n\r\n108\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--");
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("content-type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Postman-Token", "caf6c1fb-8e5e-4a29-84f3-b407c3b0d1c8")
                .build();
        Response response = client.newCall(request).execute();

        Document doc = Jsoup.parse(response.body().string()); // получаем ID объявления
        Elements elements = doc.select("div.offer-titlebox__details");
        elements = elements.select("em").select("small");
        if(elements.text().equals("")) {
            postIdOLX = ""; // если id нет, то объявление неактивно
            isAdActive = false;
        } else {
            String tempId = elements.text();
            postIdOLX = tempId.replace("Номер объявления: ", "");
        }

        try {
            price = doc.select("div.price-label").select("strong").text(); // получаем цену
        } catch (Exception e) {
            price = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("price");
        }

        Elements table = doc.select("table.details"); // получаем марку
        Elements el = table.select("th:contains(Марка)");
        Element val = el.parents().select("td.value").first();
        try {
            brand = val.text();
        } catch (Exception e) {
            brand = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("brand");
        }

        try {
            el = table.select("th:contains(Модель)"); // получаем модель
            val = el.parents().select("td.value").first();
            model = val.text();
        } catch (Exception e) {
            model = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("model");
        }

        try {
            el = table.select("th:contains(Год выпуска)"); // получаем год выпуска
            val = el.parents().select("td.value").first();
            year = val.text();
        } catch (Exception e) {
            year = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("year");
        }

        try {
            el = table.select("th:contains(Цвет)"); // получаем цвет
            val = el.parents().select("td.value").first();
            color = val.text();
        } catch (Exception e) {
            color = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("color");
        }

        try {
            el = table.select("th:contains(Объем двигателя)"); // получаем объём двигателя
            val = el.parents().select("td.value").first();
            capacity = val.text();
        } catch (Exception e) {
            capacity = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("capacity");
        }

        try {
            el = table.select("th:contains(Пробег)"); // получаем пробег
            val = el.parents().select("td.value").first();
            mileage = val.text();
        } catch (Exception e) {
            mileage = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("mileage");
        }

        try {
            el = table.select("th:contains(Тип кузова)"); // получаем тип кузова
            val = el.parents().select("td.value").first();
            bodyType = val.text();
        } catch (Exception e) {
            bodyType = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("bodyType");
        }

        try {
            el = table.select("th:contains(Вид топлива)"); // получаем вид топлива
            val = el.parents().select("td.value").first();
            fuel = val.text();
        } catch (Exception e) {
            fuel = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("fuel");
        }

        try {
            el = table.select("th:contains(Коробка передач)"); // получаем коробку передач
            val = el.parents().select("td.value").first();
            gearBox = val.text();
        } catch (Exception e) {
            gearBox = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("gearBox");
        }

        try {
            city = doc.select("a.show-map-link").select("strong").text(); // получаем город
            city = city.substring(0, city.indexOf(","));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("city");
        }

        try {
            elements = doc.select("div.offer-titlebox__details").select("em"); // получаем дату публикации
            datePublicated = elements.text();
            String[] parts = datePublicated.split(",");
            datePublicated = parts[1];
        } catch (Exception e) {
            datePublicated = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("datePublicated");
        }

        try {
            val = doc.select("div#photo-gallery-opener").select("img").first(); // получаем ссылку на фото
            image = val.absUrl("src");
        } catch (Exception e) {
            image = "";
            System.out.println(e.getMessage());
            System.out.println(url);
            System.out.println("image");
        }

        if(shouldParsePhone) { // если параметр true - парсим номер телефона, false - записываем пустую строку
            driver = new ChromeDriver(); // инициализируем драйвер
            try {
                driver.manage().window().setPosition(new Point(-2000, 0)); // ставим координаты, чтобы не видно было
                driver.get(url); // открываем страницу
                driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS); // ждём пока прогрузится
                WebElement webElement = driver.findElement // ищем элемент через xpath
                        (By.xpath("//*[@id=\"contact_methods\"]/li[2]/div"));
                driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS); // опять ждём
                webElement.click(); // совершаем клик по элементу
                Thread.sleep(4000); // ждём 4 секунды, чтобы div с номером успел обновиться
                Document page = Jsoup.parse(driver.getPageSource()); // берём страницу с полученным номером
                Elements phoneElement = page.select("div.contact-button.link-phone.atClickTracking.contact-a");
                phone = phoneElement.text(); // парсим номер телефона
                phone = phone.replace(" Показать", ""); // обрезаем ненужное
                driver.close(); // закрываем драйвер, чтобы не плодить экземпляры браузера
            } catch (Exception e) {
                driver.close(); // если попадает сюда, значит объявление не активно
                System.out.println(e.getMessage());
                System.out.println(url);
                phone = "";
                isAdActive = false;
            }
        } else {
            phone = "";
        }

        if(isAdActive) {
            CarOLX carOLX = new CarOLX(brand, model, image, price, year, color, capacity, mileage, bodyType, fuel, gearBox, city, phone, datePublicated, postIdOLX);
            listOlx.add(carOLX);
        }
    }
}


