package com.mindhub.homebanking.services.Implementation;

import com.mindhub.homebanking.DTOs.CardDTO;
import com.mindhub.homebanking.DTOs.CreateCardDTO;
import com.mindhub.homebanking.models.Card;
import com.mindhub.homebanking.models.CardColor;
import com.mindhub.homebanking.models.CardType;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.repositories.CardRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.services.CardService;
import com.mindhub.homebanking.services.ClientService;
import com.mindhub.homebanking.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.List;


import static java.util.stream.Collectors.toList;

@Service
public class CardServiceImp implements CardService {

  @Autowired
  private ClientRepository clientRepository;

  @Autowired
  private CardRepository cardRepository;

  @Autowired
  private ClientService clientService;

  public ResponseEntity<?> getCards(Authentication authentication) {
    Client client = clientService.getActualClient(authentication);
    List<CardDTO> cardsDtoList = getCardsByAuthenticatedClient(client);

    if (!cardsDtoList.isEmpty()) {
      return new ResponseEntity<>(cardsDtoList, HttpStatus.OK);
    } else {
      return new ResponseEntity<>("Client has no cards", HttpStatus.NOT_FOUND);
    }
  }

  public ResponseEntity<?> createCardForAuthenticatedClient(Authentication authentication,
                                                            @RequestBody CreateCardDTO createCardDTO) {
    // Obtener el cliente actualmente autenticado
    Client client = getAuthenticatedClientByEmail(authentication);

    // Validar que los inputs del usuario no estén vacíos
    if (createCardDTO.cardType().isBlank()) {
      return new ResponseEntity<>("Card Type input can't be empty, try again", HttpStatus.FORBIDDEN);
    }

    if (createCardDTO.cardColor().isBlank()) {
      return new ResponseEntity<>("Color input can't be empty, try again", HttpStatus.FORBIDDEN);
    }

    // Convertir los valores de cardType y cardColor a los tipos de enumeración correspondientes
    CardType cardType = CardType.valueOf(createCardDTO.cardType().toUpperCase());
    CardColor cardColor = CardColor.valueOf(createCardDTO.cardColor().toUpperCase());

    /* if (client.getCards().size() >= 3) {
      return new ResponseEntity<>("Client already has 3 cards", HttpStatus.FORBIDDEN);
    } */

    // Contadores para llevar un registro de la cantidad de tarjetas de cada tipo
    int debitCardsCount = 0;
    int creditCardsCount = 0;

    // Iterar sobre las tarjetas del cliente y contar cuántas tarjetas tiene de cada tipo
    for (Card card : client.getCards()) {
      if (card.getCardType() == CardType.DEBIT) {
        debitCardsCount++;
      } else if (card.getCardType() == CardType.CREDIT) {
        creditCardsCount++;
      }
    }

    // Verificar si el cliente ya tiene tres tarjetas del mismo tipo
    if ((cardType == CardType.DEBIT && debitCardsCount >= 3) ||
            (cardType == CardType.CREDIT && creditCardsCount >= 3)) {
      return new ResponseEntity<>("Client already has 3 cards of the same type", HttpStatus.FORBIDDEN);
    }

    // Verificar si el cliente ya tiene una tarjeta del mismo tipo y color
    boolean cardExists = client.getCards().stream()
            .anyMatch(card -> card.getCardType() == cardType && card.getCardColor() == cardColor);
    if (cardExists) {
      return new ResponseEntity<>("Client already has this card, consider requesting a different one", HttpStatus.FORBIDDEN);
    }

    // Generar un número de tarjeta único
    String cardNumber;
    do {
      cardNumber = Utils.generateCardNumber();
    } while (existsByNumber(cardNumber));

    int ccv = Utils.generateCcv();
    LocalDate fromDate = LocalDate.now();
    LocalDate thruDate = fromDate.plusYears(5);

    Card newCard = new Card(client, cardType, cardColor, cardNumber, ccv, thruDate, fromDate);
    client.addCard(newCard);
    clientService.saveClient(client);
    saveCard(newCard);

    return new ResponseEntity<>("Card created for authenticated client", HttpStatus.CREATED);
  }

  @Override
  public Client getAuthenticatedClientByEmail (Authentication authentication) {
    return clientRepository.findByEmail(authentication.getName());
  }

  @Override
  public void saveCard (Card card) {
    cardRepository.save(card);
  }

  @Override
  public boolean existsByNumber (String cardNumber) {
    return cardRepository.existsByNumber(cardNumber);
  }

  @Override
  public List<CardDTO> getCardsByAuthenticatedClient(Client client){
    return cardRepository.findByClient(client)
            .stream()
            .map(CardDTO::new)
            .collect(toList());
  }
}
