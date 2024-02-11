package com.testing.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Класс для работы с API Честного знака
 */
public class CrptApi {
    private static final String DOCUMENT_CREATING_URI = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final Logger logger = Logger.getLogger(CrptApi.class.getName());
    private final Semaphore semaphore;

    /**
     * Создает экземпляр класса и настраивает {@link Semaphore} с указанными количеством запросов и длительностью
     * интервалов для сброса ограничений. Сброс ограничений обеспечивается {@link Executors#newScheduledThreadPool(int)}
     *
     * @param timeUnit     Тип единицы времени для определения интервала
     * @param duration     Интервал времени для сброса ограничений
     * @param requestLimit Максимальное количество запросов для одного интервала времени
     * @throws IllegalArgumentException в случае передачи нулевого или отрицательного максимального количетсва запросов
     */
    public CrptApi(TimeUnit timeUnit, long duration, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Некорректное количество запросов");
        }
        this.semaphore = new Semaphore(requestLimit);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0, duration, timeUnit);
    }

    /**
     * Подгатавливает полученный {@link Document} к отправке через HTTP-запрос к API
     *
     * @param document  документ
     * @param signature подпись для документа
     * @throws JsonProcessingException
     */
    public void sendDocument(Document document, String signature) throws JsonProcessingException {
        try {
            semaphore.acquire();
            logger.info("Доступ разрешен, выполняется запрос к API");
            String jsonDocument = Document.convertDocumentToJson(document);
            executeRequestToApi(jsonDocument, signature);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Превышено допустимое количество запросов: " + e.getMessage());
        }
    }

    /**
     * Выполняет HTTP-запрос к API
     *
     * @param jsonDocument документ в формте JSON
     * @param signature    подпись для документа
     * @throws InterruptedException в случае отмены запроса
     */
    public void executeRequestToApi(String jsonDocument, String signature) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = buildRequest(jsonDocument, signature);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            handleResponse(response);
        } catch (InterruptedException | IOException e) {
            logger.warning(e.getMessage());
        }
    }

    /**
     * Генерирует HTTP-запрос
     *
     * @param jsonDocument документ в формте JSON
     * @param signature    подпись для документа
     * @return сгенерированный HttpRequest
     */
    private HttpRequest buildRequest(String jsonDocument, String signature) {
        return HttpRequest.newBuilder()
                .uri(URI.create(DOCUMENT_CREATING_URI))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();
    }

    /**
     * Обрабатывает ответ, полученный от API
     *
     * @param response полученный ответ от API
     */
    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() == 200) {
            logger.info("Ответ от API получен");
        } else {
            logger.warning("Запрос не выполнен, код ответа: " + response.statusCode());
        }
    }

    /**
     * Класс документа
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private Boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;

        /**
         * Конвертирует {@link Document} в JSON формат для передачи через HTTP запрос
         *
         * @param document экземпляр класса {@link Document}
         * @return документ в формте JSON
         * @throws JsonProcessingException
         */
        static String convertDocumentToJson(Document document) throws JsonProcessingException {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(document);
        }
    }

    /**
     * Класс продукта документа
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    /**
     * Класс описания документа
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }
}
