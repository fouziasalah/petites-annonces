package fr.petitesannonces.exception;

import fr.petitesannonces.dto.ErreurChamp;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Toutes les erreurs de l'API au format RFC 9457 (ProblemDetail).
 *
 * <p>{@link ResponseEntityExceptionHandler} traite déjà les erreurs Spring MVC (JSON illisible,
 * paramètre mal typé, route inconnue…) en ProblemDetail ; on y ajoute nos exceptions métier et
 * le détail des champs en erreur pour les 400 de validation.
 *
 * <p>Volontairement, aucun {@code @ExceptionHandler(Exception.class)} : il intercepterait les
 * {@code AccessDeniedException} levées par {@code @PreAuthorize}, qui doivent remonter jusqu'à
 * Spring Security (401/403).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail introuvable(RessourceIntrouvableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflitException.class)
    ProblemDetail conflit(ConflitException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Filet de sécurité : contrainte SQL violée malgré les vérifications (accès concurrents). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Opération incompatible avec les données existantes.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail modificationConcurrente(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La ressource a été modifiée entre-temps, réessayez.");
    }

    @ExceptionHandler(ChampInvalideException.class)
    ProblemDetail champInvalide(ChampInvalideException ex) {
        return requeteInvalide(List.of(new ErreurChamp(ex.getChamp(), ex.getMessage())));
    }

    /** Corps JSON invalide (@Valid @RequestBody) : un élément par champ en erreur (MT-07, MT-10). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErreurChamp> erreurs = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErreurChamp(error.getField(), error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, requeteInvalide(erreurs), headers, status, request);
    }

    /** Paramètre de requête invalide (ex. size > 50). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErreurChamp> erreurs = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErreurChamp(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, requeteInvalide(erreurs), headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail requeteInvalide(List<ErreurChamp> erreurs) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La requête contient des données invalides.");
        problem.setProperty("errors", erreurs);
        return problem;
    }
}
