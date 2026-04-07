package com.example.celcoinmock.config;

import com.example.celcoinmock.entity.PixKey;
import com.example.celcoinmock.repository.PixKeyRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Seeds known PIX keys on startup.
 * Only these keys will return success in DICT lookup — all others return PIE001.
 */
@Configuration
public class PixKeySeeder {

    @Bean
    public ApplicationRunner seedPixKeys(PixKeyRepository repo) {
        return args -> {
            List<Object[]> seeds = List.of(
                // keyValue, keyType, ownerName, ownerDoc, ownerType, ispb, branch, account, accountType
                new Object[]{"12345678901",                        "CPF",   "João da Silva",      "12345678901", "NATURAL_PERSON", "30306294", "0001", "00100200300", "CACC"},
                new Object[]{"98765432100",                        "CPF",   "Maria Oliveira",     "98765432100", "NATURAL_PERSON", "60701190", "0001", "00400500600", "CACC"},
                new Object[]{"+5511999990001",                     "PHONE", "Carlos Souza",       "11122233344", "NATURAL_PERSON", "18236120", "0001", "00700800900", "CACC"},
                new Object[]{"+5521988887777",                     "PHONE", "Ana Paula Ferreira", "55566677788", "NATURAL_PERSON", "00000000", "0001", "01001200300", "CACC"},
                new Object[]{"pix@celcoin.com.br",                 "EMAIL", "Tech Ltda",          "12345678000195", "LEGAL_PERSON",  "13140088", "0001", "88800000001", "CACC"},
                new Object[]{"teste@pagamento.com.br",             "EMAIL", "Fernanda Lima",      "33344455566", "NATURAL_PERSON", "30306294", "0001", "55566677788", "CACC"},
                new Object[]{"550e8400-e29b-41d4-a716-446655440000", "EVP", "Douglas Nogueira de Melo", "12345678901", "NATURAL_PERSON", "30306294", "0001", "67064118346", "CACC"}
            );

            for (Object[] s : seeds) {
                String key = (String) s[0];
                if (!repo.existsByKeyValue(key)) {
                    PixKey pk = new PixKey();
                    pk.setKeyValue(key);
                    pk.setKeyType((String) s[1]);
                    pk.setOwnerName((String) s[2]);
                    pk.setOwnerDocument((String) s[3]);
                    pk.setOwnerType((String) s[4]);
                    pk.setParticipantIspb((String) s[5]);
                    pk.setBranch((String) s[6]);
                    pk.setAccountNumber((String) s[7]);
                    pk.setAccountType((String) s[8]);
                    repo.save(pk);
                }
            }
        };
    }
}
