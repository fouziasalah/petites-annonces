package fr.petitesannonces.annonce;

import fr.petitesannonces.common.RessourceIntrouvableException;

import java.util.UUID;

public class AnnonceIntrouvableException extends RessourceIntrouvableException {

    public AnnonceIntrouvableException(UUID id) {
        super("Annonce " + id + " introuvable.");
    }
}
