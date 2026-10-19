import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ProblemDetail } from './models';

/**
 * Reporte sur les champs du formulaire les erreurs 400 renvoyées par le backend
 * (ProblemDetail.errors). Le serveur reste la référence : la validation côté front
 * n'est qu'un confort, qui peut diverger ou être contournée.
 *
 * @return true si au moins une erreur de champ a été appliquée.
 */
export function appliquerErreursServeur(error: unknown, form: FormGroup): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 400) {
    return false;
  }
  const problem = error.error as ProblemDetail | null;
  let appliquee = false;
  for (const { champ, message } of problem?.errors ?? []) {
    const control: AbstractControl | null = form.get(champ);
    if (control) {
      control.setErrors({ ...control.errors, serveur: message });
      control.markAsTouched();
      appliquee = true;
    }
  }
  return appliquee;
}
