package com.fintech.prototype.consulta.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.prototype.consulta.dto.CommomDataDTO;
import com.fintech.prototype.consulta.dto.ConsultRequestDTO;
import com.fintech.prototype.consulta.dto.ConsultResponseDTO;
import com.fintech.prototype.consulta.dto.ErrorResponseDTO;
import com.fintech.prototype.consulta.repository.RedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class MessageProcessor {

    private final String SUBSCRIBE_QUEUE = "queue-consult-rabbit";

    private final String RESPONSE_EXCHANGE = "reply-consult-rabbit";

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    private final RedisRepository redisRepository;

    public MessageProcessor(RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper, RedisRepository redisRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.redisRepository = redisRepository;
    }

    @RabbitListener(queues = SUBSCRIBE_QUEUE)
    public void processMessage(Message message) {

        String body = new String(message.getBody());
        Map<String, Object> requestHeaders = message.getMessageProperties().getHeaders();

        log.info("Received message. body: {} | headers: {}", body, requestHeaders);

        try {

            String identifier = (String) requestHeaders.get("IDENTIFIER");

            log.info("Starting convert request body to ConsultRequestDTO... identifier: {}", identifier);

            ConsultRequestDTO consultRequest = objectMapper.readValue(body, ConsultRequestDTO.class);

            log.info("Starting consult processing... identifier: {}", identifier);

            ConsultResponseDTO response = process(consultRequest, identifier);

            log.info("Starting response processing... identifier: {}", identifier);

            sentResponse(requestHeaders, response);

        } catch (Exception e) {
            handleException(e, requestHeaders);
        }

    }

    private void sentResponse(Map<String, Object> requestHeaders, ConsultResponseDTO response) {

        sentRabbimq(requestHeaders, response);

    }

    private void handleException(Exception e, Map<String, Object> headers) {

        log.error("Ocorreu um erro não esperado. identifier: {} | error: {}", headers.get("IDENTIFIER"), e.toString());

        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .error("ERROR INTERNO")
                .details(e.getMessage())
                .build();

        sentRabbimq(headers, errorResponse);

    }

    private void sentRabbimq(Map<String, Object> headers, Object response) {
        try {

            String payload = objectMapper.writeValueAsString(response);

            rabbitTemplate.convertAndSend(
                    RESPONSE_EXCHANGE + "-" + headers.get("IDENTIFIER"),
                    "",
                    payload
            );

            log.info("Response has been sent: {} | identifier: {}", payload, headers.get("IDENTIFIER"));
        } catch (Throwable exception) {
            log.error("Erro ao enviar resposta: {}", exception.getMessage());
        }
    }

    private ConsultResponseDTO process(ConsultRequestDTO request, String identifier) {

        ConsultResponseDTO response = ConsultResponseDTO.builder()
                .name("Teste")
                .document("03900000000")
                .build();

        CommomDataDTO commomData = CommomDataDTO.builder()
                .identifier(identifier)
                .agency(request.getAgency())
                .account(request.getAccount())
                .name(response.getName())
                .document(response.getDocument())
                .build();

        log.info("Starting saving redis... identifier: {}", identifier);

        redisRepository.save(commomData);

        return response;
    }
}
