package fr.petitesannonces.exception;

// Classes du projet
import fr.petitesannonces.dto.ErreurChamp;
// Spring Framework : exceptions de base de données
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
// Spring Web : réponses HTTP et format d'erreur ProblemDetail (RFC 9457)
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
// Spring Web MVC : interception des exceptions levées par les contrôleurs
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
// Java standard
import java.util.List;

/**
 * Transforme les exceptions de l'application en réponses HTTP d'erreur au format JSON standard
 * <b>ProblemDetail</b> (RFC 9457), par exemple :
 * <pre>{"type": "about:blank", "title": "Not Found", "status": 404, "detail": "Annonce … introuvable."}</pre>
 *
 * <p>Rôle : centraliser la gestion des erreurs. Les services lèvent simplement une exception
 * ({@code throw new ConflitException(...)}), et cette classe choisit le code HTTP et le corps.
 *
 * <p>Utilisé par : Spring Web, automatiquement, dès qu'un contrôleur laisse échapper une exception.
 *
 * <p>Il n'y a volontairement pas de gestion générique de {@code Exception} : elle intercepterait
 * aussi les refus de {@code @PreAuthorize}, qui doivent remonter jusqu'à Spring Security (401/403).
 */
// @RestControllerAdvice (Spring Web) : s'applique à tous les @RestController ; les méthodes
// @ExceptionHandler ci-dessous y attrapent les exceptions et produisent une réponse JSON.
@RestControllerAdvice
// extends ResponseEntityExceptionHandler (Spring Web) : classe de base qui gère déjà en
// ProblemDetail les erreurs techniques de Spring (JSON illisible, paramètre mal typé, URL inconnue…).
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Ressource absente (annonce, catégorie) → 404 Not Found. */
    // @ExceptionHandler (Spring Web) : cette méthode traite les exceptions du type indiqué,
    // y compris ses sous-classes (ex. AnnonceIntrouvableException).
    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail introuvable(RessourceIntrouvableException ex) {
        // ProblemDetail.forStatusAndDetail (Spring Web) : crée le corps d'erreur avec le code et le message.
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Action incompatible avec l'état des données (catégorie utilisée, annonce déjà publiée…) → 409 Conflict. */
    @ExceptionHandler(ConflitException.class)
    ProblemDetail conflit(ConflitException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Contrainte SQL violée (ex. libellé en double inséré en même temps par deux requêtes) → 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Opération incompatible avec les données existantes.");
    }

    /** Modification concurrente détectée par {@code @Version} sur une entité → 409. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail modificationConcurrente(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La ressource a été modifiée entre-temps, réessayez.");
    }

    /** Champ syntaxiquement correct mais refusé par le métier (ex. catégorie inconnue) → 400. */
    @ExceptionHandler(ChampInvalideException.class)
    ProblemDetail champInvalide(ChampInvalideException ex) {
        return requeteInvalide(List.of(new ErreurChamp(ex.getChamp(), ex.getMessage())));
    }

    /**
     * Corps JSON invalide (règles de {@code @Valid @RequestBody}) → 400, avec un élément par champ.
     * Exemple : titre trop court et prix négatif donnent deux entrées dans {@code errors}.
     */
    // @Override : on remplace le comportement par défaut de ResponseEntityExceptionHandler pour
    // ajouter la liste des champs en erreur.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // getBindingResult().getFieldErrors() : liste des champs qui ne respectent pas leurs règles.
        List<ErreurChamp> erreurs = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErreurChamp(error.getField(), error.getDefaultMessage()))
                .toList();
        // handleExceptionInternal (classe parente) : construit la ResponseEntity finale.
        return handleExceptionInternal(ex, requeteInvalide(erreurs), headers, status, request);
    }

    /**
     * Paramètre d'URL invalide (ex. {@code ?size=51} alors que le maximum est 50) → 400.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Pour chaque paramètre en erreur, on récupère son nom et ses messages.
        List<ErreurChamp> erreurs = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErreurChamp(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, requeteInvalide(erreurs), headers, HttpStatus.BAD_REQUEST, request);
    }

    // Construit une erreur 400 avec la liste des champs en erreur dans la propriété « errors ».
    private static ProblemDetail requeteInvalide(List<ErreurChamp> erreurs) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La requête contient des données invalides.");
        // setProperty (Spring Web) : ajoute un champ libre au JSON de l'erreur.
        problem.setProperty("errors", erreurs);
        return problem;
    }
}
