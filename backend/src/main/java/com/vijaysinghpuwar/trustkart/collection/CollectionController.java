package com.vijaysinghpuwar.trustkart.collection;

import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collection")
@Tag(name = "Collection", description = "Products acquired through virtual purchases. Values are virtual.")
class CollectionController {

    private final CollectionService collections;
    private final Achievements achievements;
    private final ShopperService shoppers;

    CollectionController(CollectionService collections, Achievements achievements, ShopperService shoppers) {
        this.collections = collections;
        this.achievements = achievements;
        this.shoppers = shoppers;
    }

    @GetMapping
    CollectionService.CollectionView collection(HttpServletRequest request) {
        return shoppers.current(request).map(s -> collections.view(s.getId()))
                .orElse(new CollectionService.CollectionView(List.of(),
                        new CollectionService.Stats(0, 0, 0, "0.00", "0.00", null, null, null), achievements.preview(), true));
    }
}
