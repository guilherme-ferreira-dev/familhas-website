import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationRegion } from './core/notifications/notification-region';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, NotificationRegion],
  template: `<router-outlet /><app-notification-region />`,
})
export class App {}
