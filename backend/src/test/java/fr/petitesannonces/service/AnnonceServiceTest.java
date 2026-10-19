package fr.petitesannonces.service;

// Classes du projet
import fr.petitesannonces.dto.AnnonceRequest;
import fr.petitesannonces.dto.AnnonceResponse;
import fr.petitesannonces.dto.PageResponse;
import fr.petitesannonces.entity.Annonce;
import fr.petitesannonces.entity.Categorie;
import fr.petitesannonces.entity.StatutAnnonce;
import fr.petitesannonces.exception.AnnonceIntrouvableException;
import fr.petitesannonces.exception.ChampInvalideException;
import fr.petitesannonces.repository.AnnonceRepository;
import fr.petitesannonces.repository.CategorieRepository;
// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
// Mockito (fourni par spring-boot-starter-test) : faux objets (« mocks ») pour isoler le service
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// Spring Data : pages de résultats
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
// Java standard
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// AssertJ et Mockito : vérifications et configuration des mocks
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AnnonceService}.
 *
 * <p>Les repositories sont remplacés par des <b>mocks</b> Mockito : de faux objets dont on
 * choisit les réponses ({@code when(...).thenReturn(...)}) et dont on vérifie les appels
 * ({@code verify(...)}). Aucune base de données n'est utilisée : on teste uniquement la logique
 * du service. Lancement : {@code ./mvnw test}.
 */
// @ExtendWith(MockitoExtension) (Mockito) : crée les @Mock et les injecte dans @InjectMocks.
@ExtendWith(MockitoExtension.class)
class AnnonceServiceTest {

    // @Mock (Mockito) : faux repository ; par défaut ses méthodes ne font rien et renvoient null/vide.
    @Mock
    private AnnonceRepository annonces;
    @Mock
    private CategorieRepository categories;

    // @InjectMocks (Mockito) : crée un vrai AnnonceService en lui passant les deux mocks ci-dessus.
    @InjectMocks
    private AnnonceService service;

    private final Categorie vehicules = new Categorie("Véhicules");

    // Crée une annonce en attente appartenant à alice.
    private Annonce annonceEnAttente() {
        return new Annonce("Vélo de course", "Très bon état", new BigDecimal("250"), vehicules, "alice-sub");
    }

    @Test
    @DisplayName("creer : enregistre l'annonce en attente, avec l'auteur fourni et le texte nettoyé")
    void creer() {
        // Préparation : la catégorie 2 existe, et save renvoie l'annonce qu'on lui donne.
        when(categories.findById(2L)).thenReturn(Optional.of(vehicules));
        when(annonces.save(any(Annonce.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnnonceRequest request = new AnnonceRequest("  Vélo de course  ", " Très bon état ", new BigDecimal("250"), 2L);

        AnnonceResponse reponse = service.creer(request, "alice-sub");

        // ArgumentCaptor (Mockito) : récupère l'objet réellement passé à save pour l'examiner.
        ArgumentCaptor<Annonce> enregistree = ArgumentCaptor.forClass(Annonce.class);
        verify(annonces).save(enregistree.capture());
        assertThat(enregistree.getValue().getAuteurId()).isEqualTo("alice-sub");
        // Les espaces en début et fin ont été retirés.
        assertThat(enregistree.getValue().getTitre()).isEqualTo("Vélo de course");
        assertThat(enregistree.getValue().getDescription()).isEqualTo("Très bon état");
        assertThat(reponse.statut()).isEqualTo(StatutAnnonce.EN_ATTENTE);
    }

    @Test
    @DisplayName("creer : catégorie inconnue → ChampInvalideException sur categorieId, rien n'est enregistré")
    void creerAvecCategorieInconnue() {
        when(categories.findById(999L)).thenReturn(Optional.empty());
        AnnonceRequest request = new AnnonceRequest("Vélo de course", "x", BigDecimal.ONE, 999L);

        assertThatThrownBy(() -> service.creer(request, "alice-sub"))
                .isInstanceOf(ChampInvalideException.class)
                // extracting : lit une propriété de l'exception (ici getChamp()).
                .extracting("champ").isEqualTo("categorieId");
        // never() : la méthode save ne doit jamais avoir été appelée.
        verify(annonces, never()).save(any());
    }

    @Test
    @DisplayName("listerPubliees sans filtre : annonces PUBLIEE, page demandée, plus récentes d'abord")
    void listerPubliees() {
        PageRequest attendu = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dateCreation"));
        when(annonces.findByStatut(StatutAnnonce.PUBLIEE, attendu))
                .thenReturn(new PageImpl<>(List.of(annonceEnAttente()), attendu, 1));

        PageResponse<AnnonceResponse> page = service.listerPubliees(null, 0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        // Sans catégorie, le filtre par catégorie ne doit pas être utilisé.
        verify(annonces, never()).findByStatutAndCategorieId(any(), any(), any());
    }

    @Test
    @DisplayName("listerPubliees avec une catégorie : utilise le filtre par catégorie")
    void listerPublieesParCategorie() {
        PageRequest attendu = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dateCreation"));
        when(annonces.findByStatutAndCategorieId(StatutAnnonce.PUBLIEE, 2L, attendu))
                .thenReturn(new PageImpl<>(List.of(), attendu, 0));

        PageResponse<AnnonceResponse> page = service.listerPubliees(2L, 0, 10);

        assertThat(page.content()).isEmpty();
        verify(annonces, never()).findByStatut(any(), any());
    }

    @Test
    @DisplayName("listerEnAttente : annonces EN_ATTENTE, plus anciennes d'abord")
    void listerEnAttente() {
        PageRequest attendu = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "dateCreation"));
        when(annonces.findByStatut(StatutAnnonce.EN_ATTENTE, attendu))
                .thenReturn(new PageImpl<>(List.of(annonceEnAttente()), attendu, 1));

        assertThat(service.listerEnAttente(0, 20).content()).hasSize(1);
    }

    @Test
    @DisplayName("publier : l'annonce passe à PUBLIEE")
    void publier() {
        Annonce annonce = annonceEnAttente();
        when(annonces.findById(annonce.getId())).thenReturn(Optional.of(annonce));

        AnnonceResponse reponse = service.publier(annonce.getId());

        assertThat(reponse.statut()).isEqualTo(StatutAnnonce.PUBLIEE);
    }

    @Test
    @DisplayName("publier : annonce inexistante → AnnonceIntrouvableException")
    void publierAnnonceInexistante() {
        UUID id = UUID.randomUUID();
        when(annonces.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publier(id))
                .isInstanceOf(AnnonceIntrouvableException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("rejeter : l'annonce passe à REJETEE avec le motif nettoyé")
    void rejeter() {
        Annonce annonce = annonceEnAttente();
        when(annonces.findById(annonce.getId())).thenReturn(Optional.of(annonce));

        AnnonceResponse reponse = service.rejeter(annonce.getId(), "  Photo manquante ");

        assertThat(reponse.statut()).isEqualTo(StatutAnnonce.REJETEE);
        assertThat(reponse.motifRejet()).isEqualTo("Photo manquante");
    }

    @Test
    @DisplayName("modifier : nouveau contenu et retour en attente de modération")
    void modifier() {
        Annonce annonce = annonceEnAttente();
        annonce.publier();
        when(annonces.findById(annonce.getId())).thenReturn(Optional.of(annonce));
        when(categories.findById(2L)).thenReturn(Optional.of(vehicules));

        AnnonceResponse reponse = service.modifier(annonce.getId(),
                new AnnonceRequest("Vélo de route", "Révisé", new BigDecimal("230"), 2L));

        assertThat(reponse.titre()).isEqualTo("Vélo de route");
        assertThat(reponse.statut()).isEqualTo(StatutAnnonce.EN_ATTENTE);
    }

    @Test
    @DisplayName("supprimer : supprime l'annonce trouvée")
    void supprimer() {
        Annonce annonce = annonceEnAttente();
        when(annonces.findById(annonce.getId())).thenReturn(Optional.of(annonce));

        service.supprimer(annonce.getId());

        verify(annonces).delete(annonce);
    }
}
