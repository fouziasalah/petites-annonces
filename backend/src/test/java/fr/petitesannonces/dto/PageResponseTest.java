package fr.petitesannonces.dto;

// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
// Spring Data : page de résultats
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
// Java standard
import java.util.List;

// AssertJ
import static org.assertj.core.api.Assertions.assertThat;

/** Test unitaire de {@link PageResponse#of} : conversion d'une page Spring Data. */
class PageResponseTest {

    @Test
    @DisplayName("of : convertit chaque élément et recopie les informations de pagination")
    void of() {
        // Page n°1 (la deuxième) de 2 éléments, sur un total de 5 éléments.
        PageImpl<Integer> page = new PageImpl<>(List.of(10, 20), PageRequest.of(1, 2), 5);

        // Integer::toHexString sert de fonction de conversion : 10 → "a", 20 → "14".
        PageResponse<String> reponse = PageResponse.of(page, Integer::toHexString);

        assertThat(reponse.content()).containsExactly("a", "14");
        assertThat(reponse.page()).isEqualTo(1);
        assertThat(reponse.size()).isEqualTo(2);
        assertThat(reponse.totalElements()).isEqualTo(5);
        // 5 éléments par pages de 2 → 3 pages.
        assertThat(reponse.totalPages()).isEqualTo(3);
    }
}
