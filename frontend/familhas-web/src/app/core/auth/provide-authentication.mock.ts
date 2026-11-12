import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { AuthService } from './auth.service';
import { MockAuthService } from './mock-auth.service';

export function provideAuthentication(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: AuthService, useClass: MockAuthService }]);
}
