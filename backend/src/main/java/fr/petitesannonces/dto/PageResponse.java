package fr.petitesannonces.dto;

// Spring Data : page de résultats renvoyée par les repositories
import org.springframework.data.domain.Page;
// Java standard
import java.util.List;
import java.util.function.Function;

/**
 * Page de résultats renvoyée au client en JSON.
 * Exemple : {@code {"content": [...], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5}}.
 *
 * <p>Rôle : exposer une pagination au format stable. On ne renvoie pas directement l'objet
 * {@link Page} de Spring Data, dont le format JSON peut changer d'une version à l'autre.
 *
 * <p>Utilisé par : les services, avec {@link #of(Page, Function)}.
 *
 * @param <T> type des éléments de la page (ex. AnnonceResponse)
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Convertit une page d'entités en page de DTO.
     * Exemple : {@code PageResponse.of(pageAnnonces, AnnonceResponse::from)}.
     *
     * @param page   page renvoyée par un repository Spring Data
     * @param mapper fonction de conversion d'un élément (entité → DTO)
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        // On convertit chaque élément, puis on recopie les informations de pagination.
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
