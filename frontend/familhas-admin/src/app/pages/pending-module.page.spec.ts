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

  it('módulo pendente diz o que falta, sem botão de ação', () => {
    const text = render(PENDING_MODULES.importarDados);
    expect(text).toContain('Importar dados');
    expect(text).toContain('Bloqueado');
  });

  it('módulo bloqueado diz o que falta', () => {
    expect(render(PENDING_MODULES.salvarImagens)).toContain('Story Image Saver');
    expect(render(PENDING_MODULES.importarDados)).toContain('dados legados');
  });
});
