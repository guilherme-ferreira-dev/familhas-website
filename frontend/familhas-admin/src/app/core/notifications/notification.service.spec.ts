import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('não repete a mesma mensagem, guarda no máximo 3 e some sozinho', () => {
    const service = TestBed.inject(NotificationService);
    service.error('a');
    service.error('a');
    service.info('b');
    service.info('c');
    service.info('d');
    expect(service.notices().map((n) => n.text)).toEqual(['b', 'c', 'd']);
    vi.advanceTimersByTime(8000);
    expect(service.notices()).toEqual([]);
  });
});
