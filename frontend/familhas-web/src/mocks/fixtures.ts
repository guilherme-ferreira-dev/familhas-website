import type {
  Category,
  City,
  Flight,
  CarRentalOffer,
  Hotel,
  Me,
  RoomOffer,
  TravelPackage,
} from '../app/core/api/api-types';

/** Dados fictícios para o build `mock`. Tipados pelo OpenAPI: se o contrato mudar, o build quebra aqui. */
export const CITY = {
  saoPaulo: '0b0c0000-0000-4000-8000-000000000001',
  brasilia: '0b0c0000-0000-4000-8000-000000000002',
  maceio: '0b0c0000-0000-4000-8000-000000000003',
  portoSeguro: '0b0c0000-0000-4000-8000-000000000004',
  gramado: '0b0c0000-0000-4000-8000-000000000005',
} as const;

export const CATEGORY = {
  praia: '0ca70000-0000-4000-8000-000000000001',
  serra: '0ca70000-0000-4000-8000-000000000002',
} as const;

export const cities: City[] = [
  { id: CITY.saoPaulo, name: 'São Paulo', country: 'Brasil', cityType: 'ORIGIN', status: 'ACTIVE' },
  { id: CITY.brasilia, name: 'Brasília', country: 'Brasil', cityType: 'ORIGIN', status: 'ACTIVE' },
  { id: CITY.maceio, name: 'Maceió', country: 'Brasil', cityType: 'DESTINATION', status: 'ACTIVE' },
  {
    id: CITY.portoSeguro,
    name: 'Porto Seguro',
    country: 'Brasil',
    cityType: 'DESTINATION',
    status: 'ACTIVE',
  },
  {
    id: CITY.gramado,
    name: 'Gramado',
    country: 'Brasil',
    cityType: 'DESTINATION',
    status: 'ACTIVE',
  },
];

export const categories: Category[] = [
  {
    id: CATEGORY.praia,
    title: 'Praia',
    status: 'ACTIVE',
    imageUrl: null,
    description: 'Sol, mar e piscina para a família toda.',
  },
  {
    id: CATEGORY.serra,
    title: 'Serra',
    status: 'ACTIVE',
    imageUrl: null,
    description: 'Clima frio, fondue e passeios.',
  },
];

export const flights: Flight[] = [
  {
    id: '0f170000-0000-4000-8000-000000000001',
    companyName: 'Azul',
    boardingDate: '2027-01-10T08:00:00',
    returnDate: '2027-01-17T18:30:00',
    boardingAirport: 'BSB',
    returnAirport: 'MCZ',
    originCityId: CITY.brasilia,
    destinationCityId: CITY.maceio,
    pricePerPassenger: 1290,
    currency: 'BRL',
    seatsAvailable: 12,
    status: 'ACTIVE',
  },
  {
    id: '0f170000-0000-4000-8000-000000000002',
    companyName: 'LATAM',
    boardingDate: '2027-02-05T06:45:00',
    returnDate: '2027-02-12T21:10:00',
    boardingAirport: 'GRU',
    returnAirport: 'BPS',
    originCityId: CITY.saoPaulo,
    destinationCityId: CITY.portoSeguro,
    pricePerPassenger: 980,
    currency: 'BRL',
    seatsAvailable: 4,
    status: 'ACTIVE',
  },
  {
    id: '0f170000-0000-4000-8000-000000000003',
    companyName: 'GOL',
    boardingDate: '2027-07-15T09:20:00',
    returnDate: '2027-07-20T16:00:00',
    boardingAirport: 'GRU',
    returnAirport: 'POA',
    originCityId: CITY.saoPaulo,
    destinationCityId: CITY.gramado,
    pricePerPassenger: 760,
    currency: 'BRL',
    seatsAvailable: 20,
    status: 'ACTIVE',
  },
];

export const hotels: Hotel[] = [
  {
    id: '0b070000-0000-4000-8000-000000000001',
    name: 'Resort Pajuçara',
    cityId: CITY.maceio,
    address: 'Av. Dr. Antônio Gouveia, Maceió',
    stars: 4,
    description: 'Pé na areia, com clube infantil.',
    imageUrl: null,
    status: 'ACTIVE',
  },
  {
    id: '0b070000-0000-4000-8000-000000000002',
    name: 'Pousada Pinheiros',
    cityId: CITY.gramado,
    address: 'Rua Coberta, Gramado',
    stars: 3,
    description: 'Lareira e café colonial.',
    imageUrl: null,
    status: 'ACTIVE',
  },
];

export const roomOffers: RoomOffer[] = [
  {
    id: '0a0f0000-0000-4000-8000-000000000001',
    hotelId: hotels[0].id,
    roomType: 'Família vista mar',
    boardType: 'ALL_INCLUSIVE',
    maxGuests: 4,
    pricePerNight: 890,
    currency: 'BRL',
    status: 'ACTIVE',
  },
  {
    id: '0a0f0000-0000-4000-8000-000000000002',
    hotelId: hotels[1].id,
    roomType: 'Chalé',
    boardType: 'BREAKFAST',
    maxGuests: 3,
    pricePerNight: 520,
    currency: 'BRL',
    status: 'ACTIVE',
  },
];

export const carOffers: CarRentalOffer[] = [
  {
    id: '0ca40000-0000-4000-8000-000000000001',
    cityId: CITY.maceio,
    companyName: 'Locadora Litoral',
    carModel: 'Spin',
    carCategory: 'MINIVAN',
    transmission: 'AUTOMATIC',
    seats: 7,
    pricePerDay: 210,
    currency: 'BRL',
    status: 'ACTIVE',
  },
  {
    id: '0ca40000-0000-4000-8000-000000000002',
    cityId: CITY.gramado,
    companyName: 'Serra Rent',
    carModel: 'Onix',
    carCategory: 'COMPACT',
    transmission: 'MANUAL',
    seats: 5,
    pricePerDay: 150,
    currency: 'BRL',
    status: 'ACTIVE',
  },
];

export const travelPackages: TravelPackage[] = [
  {
    id: '0aac0000-0000-4000-8000-000000000001',
    title: 'Maceió com tudo incluído',
    price: 4290,
    pricePromotion: 3890,
    currency: 'BRL',
    status: 'ACTIVE',
    imageUrl: null,
    flightId: flights[0].id,
    categoryId: CATEGORY.praia,
    originCityId: CITY.brasilia,
    destinationCityId: CITY.maceio,
    components: [
      {
        id: '0c0a0000-0000-4000-8000-000000000001',
        type: 'HOTEL_STAY',
        roomOfferId: roomOffers[0].id,
        carRentalOfferId: null,
        quantity: 1,
      },
      {
        id: '0c0a0000-0000-4000-8000-000000000002',
        type: 'CAR_RENTAL',
        roomOfferId: null,
        carRentalOfferId: carOffers[0].id,
        quantity: 1,
      },
    ],
  },
  {
    id: '0aac0000-0000-4000-8000-000000000002',
    title: 'Porto Seguro em família',
    price: 2980,
    pricePromotion: null,
    currency: 'BRL',
    status: 'ACTIVE',
    imageUrl: null,
    flightId: flights[1].id,
    categoryId: CATEGORY.praia,
    originCityId: CITY.saoPaulo,
    destinationCityId: CITY.portoSeguro,
    components: [],
  },
  {
    id: '0aac0000-0000-4000-8000-000000000003',
    title: 'Inverno em Gramado',
    price: 3450,
    pricePromotion: 3190,
    currency: 'BRL',
    status: 'ACTIVE',
    imageUrl: null,
    flightId: flights[2].id,
    categoryId: CATEGORY.serra,
    originCityId: CITY.saoPaulo,
    destinationCityId: CITY.gramado,
    components: [
      {
        id: '0c0a0000-0000-4000-8000-000000000003',
        type: 'HOTEL_STAY',
        roomOfferId: roomOffers[1].id,
        carRentalOfferId: null,
        quantity: 1,
      },
    ],
  },
];

export const me: Me = {
  id: '0c11e000-0000-4000-8000-000000000001',
  name: 'Cliente de Teste',
  email: 'cliente@familhas.local',
  phoneNumber: null,
  status: 'ACTIVE',
  roles: ['ROLE_USER'],
  favoriteIds: [],
  myFlightIds: [],
};
