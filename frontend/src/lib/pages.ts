/** Titre et phrase d'aide de chaque page (barre du haut sur ordinateur, en-tete de page sur mobile). */
export interface PageMeta {
  title: string
  subtitle: string
}

const PAGES: Array<[RegExp, PageMeta]> = [
  [/^\/$/, { title: 'Carte', subtitle: 'Trouvez une borne libre et lancez la charge en deux gestes.' }],
  [/^\/charge\/terminee/, { title: 'Charge terminée', subtitle: 'Le récapitulatif de votre charge et sa facture.' }],
  [/^\/charge/, { title: 'Ma charge', subtitle: 'Suivez votre charge en direct et arrêtez-la quand vous voulez.' }],
  [/^\/reservations/, { title: 'Réservations', subtitle: 'Réservez une borne pour plus tard, puis démarrez à votre arrivée.' }],
  [/^\/factures\/.+/, { title: 'Facture', subtitle: 'Le détail d’une charge : énergie, tarifs et frais d’occupation.' }],
  [/^\/historique/, { title: 'Historique et factures', subtitle: 'Vos charges passées et leurs factures.' }],
  [/^\/parametres/, { title: 'Paramètres', subtitle: 'Votre compte de démonstration et la connexion à l’API.' }],
]

export function pageMeta(pathname: string): PageMeta {
  return PAGES.find(([pattern]) => pattern.test(pathname))?.[1] ?? { title: 'VoltPark', subtitle: '' }
}
