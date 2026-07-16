package com.ulutman.service;

import com.ulutman.model.entities.AdVersiting;
import com.ulutman.model.entities.User;
import com.ulutman.model.enums.MediaFileType;
import com.ulutman.repository.AdVersitingRepository;
import com.ulutman.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import okhttp3.*;
import okhttp3.MediaType;
import okhttp3.RequestBody;

@Slf4j
@Service
public class AdVersitingService {

    private final AdVersitingRepository adVersitingRepository;
    private final UserRepository userRepository;

    private static final String ADMIN_CHAT_ID = "644781402";
    private static final String TELEGRAM_BOT_TOKEN =   "8904424094:AAHyjcK-pl-Fa0L-Vti8HubShbEBaMK5a6k";

//    private static final String ADMIN_CHAT_ID = "7825590787";
//    private static final String TELEGRAM_BOT_TOKEN = "8916468491:AAGZbYzNTZxBaqayYwl5_fYN2wYsa7BAD6s";
    //private static final String ADMIN_CHAT_ID = "6640338760"; //потом вернем
    //private static final String TELEGRAM_BOT_TOKEN = "7721979760:AAGc8x9AXc5auPzVZX8ajUQjJvXAgNpK6_g";
    private final MailingService mailingService;


    @Autowired
    private MinioService minioService;

    @Autowired
    public AdVersitingService(AdVersitingRepository adVersitingRepository, UserRepository userRepository, MailingService mailingService) throws IOException {
        this.adVersitingRepository = adVersitingRepository;
        this.userRepository = userRepository;
        this.mailingService = mailingService;
    }

    public void createAdvertising(MultipartFile imageFile, String bank, MultipartFile paymentReceiptFile, Principal principal) throws IOException, MessagingException {
        Optional<User> userOptional = userRepository.findByEmail(principal.getName());


        if (!userOptional.isPresent()) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }
        User user = userOptional.get();


        if (imageFile.isEmpty() || paymentReceiptFile.isEmpty()) {
            throw new IllegalArgumentException("Файлы изображения и квитанции не могут быть пустыми.");
        }


        BufferedImage img = ImageIO.read(imageFile.getInputStream());
        if (img == null) {
            throw new IllegalArgumentException("Не удалось прочитать изображение.");
        }


        saveAdvertisingToS3(imageFile, bank, paymentReceiptFile, user.getEmail());
        System.out.println("Реклама создана с изображением: " + imageFile.getOriginalFilename());
    }


    public void saveAdvertisingToS3(MultipartFile imageFile, String bank, MultipartFile paymentReceiptFile, String userEmail) throws IOException, MessagingException {
        Optional<User> userOptional = userRepository.findByEmail(userEmail);

        if (!userOptional.isPresent()) {
            throw new RuntimeException("Пользователь с таким email не найден.");
        }

        User user = userOptional.get();
        String userId = String.valueOf(user.getId());

        String imageKey = minioService.upload(imageFile, MediaFileType.AD_IMAGE, userId);
        String receiptKey = minioService.upload(paymentReceiptFile, MediaFileType.AD_RECEIPT, userId);

        AdVersiting ad = new AdVersiting(imageKey, true, receiptKey, bank, user);
        ad.setCreatedAt(LocalDateTime.now());
        ad.setActive(false);
        adVersitingRepository.save(ad);

        java.io.File tempReceipt = Files.createTempFile("receipt-", paymentReceiptFile.getOriginalFilename()).toFile();
        paymentReceiptFile.transferTo(tempReceipt);
        try {
            sendReceiptAsDocumentToTelegram(tempReceipt, bank, ad);
        } finally {
            tempReceipt.delete();
        }
    }

    public void sendReceiptAsDocumentToTelegram(File receiptFile, String bankName, AdVersiting adVersiting) throws MessagingException {
        if (!receiptFile.exists() || !receiptFile.canRead()) {
            throw new RuntimeException("Файл не найден или недоступен для чтения: " + receiptFile.getAbsolutePath());
        }

        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/pdf");

        RequestBody fileBody = RequestBody.create(mediaType, receiptFile);

        String messageBody = "Новый чек: \n" +
                             "Имя карты: " + bankName + "\n" +
                             "Реклама ID: " + adVersiting.getId() + "\n" +
                             "Email пользователя: " + (adVersiting.getUser() != null ? adVersiting.getUser().getEmail() : "Не указан");

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", ADMIN_CHAT_ID)
                .addFormDataPart("document", receiptFile.getName(), fileBody)
                .addFormDataPart("caption", messageBody); // Добавляем текст сообщения как caption

        Request request = new Request.Builder()
                .url(String.format("https://api.telegram.org/bot%s/sendDocument", TELEGRAM_BOT_TOKEN))
                .post(builder.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code() + ": " + responseBody);
            }
            System.out.println("Документ и сообщение успешно отправлены в Telegram. Ответ: " + responseBody);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка при отправке документа в Telegram", e);
        }
    }

//    public List<AdVersiting> getAllActiveAds() {
//        return adVersitingRepository.findAllActiveAdverting();
//    }

    public List<AdVersiting> getAllActiveAds() {
        List<AdVersiting> ads = adVersitingRepository.findAllActiveAdverting();

        ads.forEach(ad -> {
            if (ad.getImagePath() != null) {
                ad.setImagePath(minioService.presign(ad.getImagePath()));
            }
        });

        return ads;
    }

    @Transactional
    public void deleteMultipleAds(List<Long> adIds, Long userId) {

        List<AdVersiting> advertisements =
                adVersitingRepository.findAllById(adIds);


        for (AdVersiting ad : advertisements) {

            if (!ad.getUser().getId().equals(userId)) {
                throw new RuntimeException(
                        "Нет доступа к рекламе id: " + ad.getId()
                );
            }
        }


        adVersitingRepository.deleteAll(advertisements);
    }
}
