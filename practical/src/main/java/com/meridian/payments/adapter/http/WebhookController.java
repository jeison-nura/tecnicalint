package com.meridian.payments.adapter.http;

import com.meridian.payments.port.ReceivePaymentWebhookUseCase;
import com.meridian.payments.port.WebhookRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {
    private final ReceivePaymentWebhookUseCase receivePaymentWebhook;

    public WebhookController(ReceivePaymentWebhookUseCase receivePaymentWebhook) {
        this.receivePaymentWebhook = receivePaymentWebhook;
    }

    @PostMapping(path = "/webhooks/v1/payments", consumes = "application/json")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader HttpHeaders headers) {
        receivePaymentWebhook.receive(new WebhookRequest(rawBody, headers.toSingleValueMap()));
        return ResponseEntity.ok().build();
    }
}
