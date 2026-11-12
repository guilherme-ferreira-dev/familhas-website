import { isAllowedCheckoutUrl, PaymentRedirect } from './payment-redirect';

describe('isAllowedCheckoutUrl', () => {
  it.each([
    ['https://checkout.stripe.com/c/pay/cs_test_123', true],
    ['http://checkout.stripe.com/c/pay/cs_test_123', false],
    ['https://checkout.stripe.com.evil.com/x', false],
    ['https://evil.com/?https://checkout.stripe.com', false],
    ['javascript:alert(1)', false],
    ['/checkout/1/sucesso', false],
  ])('%s -> %s', (url, allowed) => {
    expect(isAllowedCheckoutUrl(url)).toBe(allowed);
  });

  it('aceita origens extras explícitas (build mock)', () => {
    expect(
      isAllowedCheckoutUrl('http://localhost:4200/checkout/1/sucesso', ['http://localhost:4200']),
    ).toBe(true);
  });
});

describe('PaymentRedirect', () => {
  class Spy extends PaymentRedirect {
    readonly visited: string[] = [];
    protected override navigate(url: string): void {
      this.visited.push(url);
    }
  }

  it('só navega para o Stripe', () => {
    const redirect = new Spy();
    redirect.go('https://checkout.stripe.com/c/pay/cs_1');
    expect(() => redirect.go('https://evil.com/pay')).toThrow(/origem não permitida/);
    expect(redirect.visited).toEqual(['https://checkout.stripe.com/c/pay/cs_1']);
  });
});
