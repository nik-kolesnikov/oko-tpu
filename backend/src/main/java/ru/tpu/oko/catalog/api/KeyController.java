package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.catalog.api.dto.ApiKeyDto;
import ru.tpu.oko.catalog.api.dto.ApiKeyIssuedDto;
import ru.tpu.oko.catalog.api.dto.KeyCreate;
import ru.tpu.oko.catalog.service.KeyService;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.ItemsDto;

/** Ответы с ключами не кэшируются (Cache-Control: no-store). */
@RestController
@RequestMapping(ApiPaths.V1 + "/products/{productId}/keys")
public class KeyController {

    private final KeyService keys;

    public KeyController(KeyService keys) {
        this.keys = keys;
    }

    @GetMapping
    public ResponseEntity<ItemsDto<ApiKeyDto>> list(@PathVariable long productId,
                                                    @RequestParam(defaultValue = "false") boolean includeRevoked) {
        return noStore(HttpStatus.OK, new ItemsDto<>(keys.list(productId, includeRevoked)));
    }

    @PostMapping
    public ResponseEntity<ApiKeyIssuedDto> create(@PathVariable long productId, @Valid @RequestBody KeyCreate request) {
        return noStore(HttpStatus.CREATED, keys.create(productId, request.type()));
    }

    @PostMapping("/{keyId}/reissue")
    public ResponseEntity<ApiKeyIssuedDto> reissue(@PathVariable long productId, @PathVariable long keyId) {
        return noStore(HttpStatus.CREATED, keys.reissue(productId, keyId));
    }

    @PostMapping("/{keyId}/revoke")
    public ResponseEntity<ApiKeyDto> revoke(@PathVariable long productId, @PathVariable long keyId) {
        return noStore(HttpStatus.OK, keys.revoke(productId, keyId));
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
