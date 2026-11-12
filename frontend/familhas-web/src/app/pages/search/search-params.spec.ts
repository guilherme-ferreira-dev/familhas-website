import { convertToParamMap } from '@angular/router';
import {
  filtersFrom,
  filtersToParams,
  invalidFields,
  missingFields,
  toApiQuery,
} from './search-params';

const from = (params: Record<string, string>) => filtersFrom(convertToParamMap(params));

describe('filtros da busca', () => {
  it('usa padrões seguros para valores ausentes ou inválidos', () => {
    const f = from({
      tipo: 'xpto',
      passageiros: '42',
      ida: '10/01/2027',
      precoMin: 'abc',
      pagina: '-3',
    });
    expect(f).toMatchObject({ tipo: 'pacotes', passageiros: 9, ida: '', precoMin: '', pagina: 1 });
  });

  it('ida e volta pela URL omitem o que está no padrão', () => {
    const params = filtersToParams(
      from({ tipo: 'voos', origem: 'a', passageiros: '1', pagina: '1' }),
    );
    expect(params).toEqual({ tipo: 'voos', origem: 'a' });
  });

  it('voo exige origem, destino e data de ida', () => {
    expect(missingFields(from({ tipo: 'voos', origem: 'a' }))).toEqual(['destino', 'data de ida']);
    expect(missingFields(from({ tipo: 'pacotes' }))).toEqual([]);
  });

  it('acusa volta antes da ida e preço máximo menor que o mínimo', () => {
    const errors = invalidFields(
      from({
        tipo: 'hoteis',
        ida: '2027-01-10',
        volta: '2027-01-09',
        precoMin: '500',
        precoMax: '100',
      }),
    );
    expect(Object.keys(errors).sort()).toEqual(['precoMax', 'volta']);
  });

  it('hotel e carro precisam de ao menos uma diária', () => {
    expect(
      invalidFields(from({ tipo: 'carros', ida: '2027-01-10', volta: '2027-01-10' })).volta,
    ).toBeTruthy();
  });

  it('converte para os parâmetros da API de cada tipo', () => {
    expect(
      toApiQuery(
        from({
          tipo: 'hoteis',
          destino: 'c',
          ida: '2027-01-10',
          volta: '2027-01-12',
          passageiros: '3',
          estrelas: '4',
          pagina: '2',
        }),
      ),
    ).toMatchObject({
      cityId: 'c',
      checkIn: '2027-01-10',
      checkOut: '2027-01-12',
      guests: 3,
      minStars: '4',
      page: 1,
      sort: 'totalPrice,asc',
    });
    expect(
      toApiQuery(
        from({
          tipo: 'carros',
          destino: 'c',
          ida: 'x',
          categoriaCarro: 'SUV',
          ordem: 'preco-desc',
        }),
      ),
    ).toMatchObject({
      cityId: 'c',
      carCategory: 'SUV',
      sort: 'totalPrice,desc',
    });
    expect(toApiQuery(from({ tipo: 'pacotes', categoria: 'k', ida: '2027-01-01' }))).toMatchObject({
      categoryId: 'k',
      departureFrom: '2027-01-01',
    });
  });
});
