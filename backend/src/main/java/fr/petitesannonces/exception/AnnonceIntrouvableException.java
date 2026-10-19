package fr.petitesannonces.exception;

import fr.petitesannonces.exception.RessourceIntrouvableException;

import java.util.UUID;

public class AnnonceIntrouvableException extends RessourceIntrouvableException {

    public AnnonceIntrouvableException(UUID id) {
        super("Annonce " + id + " introuvable.");
    }
}
