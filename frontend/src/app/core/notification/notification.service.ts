import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'info' | 'succes' | 'erreur';

export interface Notification {
  id: number;
  kind: NotificationKind;
  message: string;
}

/** Messages éphémères affichés en haut de page. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly notifications = signal<Notification[]>([]);

  show(message: string, kind: NotificationKind = 'info', durationMs = 6000): void {
    const id = this.nextId++;
    this.notifications.update((list) => [...list, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), durationMs);
  }

  dismiss(id: number): void {
    this.notifications.update((list) => list.filter((n) => n.id !== id));
  }
}
