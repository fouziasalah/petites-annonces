package fr.petitesannonces.service;

// Classes du projet
import fr.petitesannonces.dto.CategorieRequest;
import fr.petitesannonces.dto.CategorieResponse;
import fr.petitesannonces.entity.Categorie;
import fr.petitesannonces.exception.ConflitException;
import fr.petitesannonces.exception.RessourceIntrouvableException;
import fr.petitesannonces.repository.AnnonceRepository;
import fr.petitesannonces.repository.CategorieRepository;
// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
// Mockito
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// Spring Data
import org.springframework.data.domain.Sort;
// Java standard
import java.util.List;

// AssertJ et Mockito
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link CategorieService}, avec des repositories simulés par Mockito.
 * Lancement : {@code ./mvnw test}.
 */
@ExtendWith(MockitoExtension.class)
class CategorieServiceTest {

    @Mock
    private CategorieRepository categories;
    @Mock
    private AnnonceRepository annonces;

    @InjectMocks
    private CategorieService service;

    @Test
    @DisplayName("lister : demande les catégories triées par libellé et les convertit en DTO")
    void lister() {
        when(categories.findAll(Sort.by("libelle")))
                .thenReturn(List.of(new Categorie("Emploi"), new Categorie("Immobilier")));

        List<CategorieResponse> resultat = service.lister();

        // extracting(CategorieResponse::libelle) : compare seulement les libellés.
        assertThat(resultat).extracting(CategorieResponse::libelle).containsExactly("Emploi", "Immobilier");
    }

    @Test
    @DisplayName("creer : enregistre la catégorie avec le libellé nettoyé")
    void creer() {
        when(categories.existsByLibelleIgnoreCase("Jardinage")).thenReturn(false);
        when(categories.save(any(Categorie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategorieResponse reponse = service.creer(new CategorieRequest("  Jardinage "));

        assertThat(reponse.libelle()).isEqualTo("Jardinage");
    }

    @Test
    @DisplayName("creer : libellé déjà existant → ConflitException, rien n'est enregistré")
    void creerDoublon() {
        when(categories.existsByLibelleIgnoreCase("Emploi")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(new CategorieRequest("Emploi")))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");
        verify(categories, never()).save(any());
    }

    @Test
    @DisplayName("supprimer : catégorie inexistante → RessourceIntrouvableException")
    void supprimerInexistante() {
        when(categories.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.supprimer(42L))
                .isInstanceOf(RessourceIntrouvableException.class);
        verify(categories, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("supprimer : catégorie utilisée par des annonces → ConflitException")
    void supprimerUtilisee() {
        when(categories.existsById(1L)).thenReturn(true);
        when(annonces.existsByCategorieId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.supprimer(1L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("utilisée");
        verify(categories, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("supprimer : catégorie inutilisée → supprimée")
    void supprimer() {
        when(categories.existsById(5L)).thenReturn(true);
        when(annonces.existsByCategorieId(5L)).thenReturn(false);

        service.supprimer(5L);

        verify(categories).deleteById(5L);
    }
}
