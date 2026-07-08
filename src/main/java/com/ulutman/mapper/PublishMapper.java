package com.ulutman.mapper;

import com.ulutman.model.dto.*;
import com.ulutman.model.entities.Publish;
import com.ulutman.model.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class PublishMapper {

    public Publish mapToEntity(PublishRequest publishRequest) {
        Publish publish = new Publish();
        publish.setTitle(publishRequest.getTitle());
        publish.setDescription(publishRequest.getDescription());
        publish.setMetro(publishRequest.getMetro());
        publish.setAddress(publishRequest.getAddress());
        publish.setPhone(publishRequest.getPhoneNumber());
        publish.setImages(publishRequest.getImages());
        publish.setPrice(publishRequest.getPrice());
        publish.setCategory(publishRequest.getCategory());
        publish.setSubCategory(publishRequest.getSubcategory());
        publishRequest.getBank().ifPresent(publish::setBank);
        publishRequest.getPaymentReceiptFile().ifPresent(paymentReceiptUrl -> publish.setPaymentReceiptUrl(String.valueOf(paymentReceiptUrl)));
        publish.setCategory(publishRequest.getCategory());
        publish.setCreateDate(LocalDate.now());
        return publish;
    }

    public PublishResponse mapToResponse(Publish publish) {
        PublishResponse.PublishResponseBuilder builder = PublishResponse.builder()
                .id(publish.getId())
                .title(publish.getTitle())
                .description(publish.getDescription())
                .category(publish.getCategory())
                .subcategory(publish.getSubCategory())
                .address(publish.getAddress())
                .phoneNumber(publish.getPhone())
                .images(publish.getImages())
                .price(publish.getPrice())
                .publishStatus(publish.getPublishStatus())
                .createDate(publish.getCreateDate())
                .detailFavorite(publish.isDetailFavorite())
                .categoryStatus(publish.getCategoryStatus())
                .active(publish.isActive())
                .metroStation(publish.getMetroStation())
                .favoriteCount(publish.getFavoriteCount())
                .nextBoostTime(publish.getNextBoostTime())
                .timeToNextBoost(publish.getTimeToNextBoost());

        if (publish.getUser() != null) {
            builder.userId(publish.getUser().getId());
            builder.userName(publish.getUser().getName());

            if (publish.getUser().getUserAccount() != null) {
                builder.accountId(publish.getUser().getUserAccount().getId());
            }

            if (publish.getUser().getPublishes() != null) {
                builder.numberOfPublications(publish.getUser().getPublishes().size());
            }
        }

        return builder.build();
    }

    public PublishDetailsResponse mapToDetailsResponse(Publish publish) {
        User user = publish.getUser();
        return PublishDetailsResponse.builder()
                .id(publish.getId())
                .userName(user != null ? user.getName() : "Неизвестно")
                .email(user != null ? user.getEmail() : "Неизвестно")
                .category(publish.getCategory())
                .createDate(publish.getCreateDate())
                .publishStatus(publish.getPublishStatus())
                .build();
    }

    public FilteredPublishResponse mapToFilteredResponse(Publish publish) {
        User user = publish.getUser();
        String userNameResult = user != null ? user.getName() : "Неизвестно";
        return FilteredPublishResponse.builder()
                .id(publish.getId())
                .userName(userNameResult)
                .title(publish.getTitle())
                .description(publish.getDescription())
                .publicationCount(user.getNumberOfPublications())
                .category(publish.getCategory())
                .createDate(publish.getCreateDate())
                .categoryStatus(publish.getCategoryStatus())
                .build();
    }
}
