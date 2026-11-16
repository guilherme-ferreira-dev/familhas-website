import { TestBed } from '@angular/core/testing';
import { PENDING_MODULES } from '../layout/modules';
import { PendingModulePage } from './pending-module.page';

describe('PendingModulePage', () => {
  function render(module: (typeof PENDING_MODULES)[keyof typeof PENDING_MODULES]) {
    const fixture = TestBed.createComponent(PendingModulePage);
    fixture.componentRef.setInput('module', module);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('diz em que etapa o módulo chega, sem botão de ação', () => {
    const text = render(PENDING_MODULES.vouchers);
    expect(text).toContain('Emitir Voucher');
    expect(text).toContain('Etapa 3');
  });

  it('módulo bloqueado diz o que falta', () => {
    expect(render(PENDING_MODULES.salvarImagens)).toContain('Story Image Saver');
    expect(render(PENDING_MODULES.importarDados)).toContain('dados legados');
  });
});
