package fr.petitesannonces.categorie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Categorie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String libelle;

    protected Categorie() {
        // requis par JPA
    }

    public Categorie(String libelle) {
        this.libelle = libelle;
    }

    public Long getId() {
        return id;
    }

    public String getLibelle() {
        return libelle;
    }
}
