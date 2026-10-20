# Fonds personnel autonome — Spécification de l'orchestrateur

Statut : **brouillon à valider** · Version 0.1 · 2026-10-20

## 1. Objectif

Un fonds personnel qui s'exécute seul sur un compte IBKR :

- l'utilisateur fait un virement mensuel (montant variable, avec un plancher) ;
- l'orchestrateur investit, rééquilibre, applique les règles de tendance et envoie un rapport ;
- objectif de long terme : environ 8 à 9 %/an au centre, 10 %+ dans un scénario favorable. Ce n'est pas une garantie ;
- perte temporaire maximale acceptée : -35 à -45 %.

Contraintes de l'utilisateur :

- pas d'obligations ;
- matières premières seulement quand leur tendance est haussière ;
- préférence pour les actions et la crypto ;
- résident fiscal géorgien, à confirmer, donc pas de contrainte fiscale sur les ventes ;
- aucun appel à un LLM pendant le fonctionnement : tout est du code déterministe.

## 2. Univers et allocation cible

| Poche | Cible | Instrument (à confirmer selon la dispo IBKR) | Règle |
|---|---|---|---|
| Actions monde | 75 % | VWCE (Vanguard FTSE All-World Acc, Xetra, EUR) | Toujours investi |
| Crypto : BTC | 10,5 % | ETP Bitcoin physique coté en Europe, ou crypto IBKR si la permission existe | Tendance hebdo |
| Crypto : ETH | 4,5 % | ETP Ether physique coté en Europe, ou crypto IBKR | Tendance hebdo |
| Matières premières | 10 % | IGLN (or), SSLN (argent), CMOD (panier Bloomberg Commodity) | Tendance mensuelle, pondération par volatilité inverse |
| Parking | reste | XEON (monétaire €STR) | Reçoit les poches dont le signal est éteint |

Tous les tickers sont des paramètres de `config.yaml`. Aucun n'est codé en dur.

## 3. Signaux de tendance

On vend parce que la hausse s'arrête, jamais parce qu'elle « a assez monté ».

| Actif | Fréquence d'évaluation | Signal ON | Signal OFF |
|---|---|---|---|
| BTC, ETH | Hebdomadaire (clôture du vendredi) | Clôture > SMA 20 semaines × (1 + b) | Clôture < SMA 20 semaines × (1 − b) |
| Or, argent, panier | Mensuelle (dernière clôture du mois) | Clôture > SMA 10 mois × (1 + b) | Clôture < SMA 10 mois × (1 − b) |

- `b` est une marge d'hystérésis, à 1 % par défaut. Entre les deux seuils, le signal garde son état précédent, ce qui évite les allers-retours inutiles.
- Les prix utilisés sont ceux de l'instrument lui-même, téléchargés depuis IBKR. Si l'historique de l'instrument est trop court, on utilise l'actif sous-jacent.
- VWCE n'a **aucun** signal : on ne fait pas de market timing sur le cœur du portefeuille.

## 4. Poids à l'intérieur des poches

- **Crypto :** BTC et ETH reçoivent leur poids cible quand leur signal est ON. Quand il est OFF, ce poids va dans XEON.
- **Matières premières :** chaque actif ON reçoit un poids proportionnel à `1 / volatilité`, la volatilité étant calculée sur 63 jours de rendements quotidiens. La somme est ramenée à 10 % et chaque actif est plafonné à 5 %. Le poids d'un actif OFF va dans XEON.
  - On ne redistribue pas le poids d'un actif OFF vers les autres matières premières. La poche ne doit pas se concentrer sur un seul actif.

## 5. Rééquilibrage (la prise de profit automatique)

On vend ce qui a monté et on rachète ce qui a baissé, mécaniquement.

1. **Versements :** le cash disponible achète d'abord les lignes les plus en retard sur leur cible, sans jamais vendre. C'est le mécanisme principal.
2. **Seuil :** le contrôle se fait chaque semaine. Une poche est rééquilibrée vers sa cible si elle sort de sa bande de tolérance, selon la règle « 5/25 » : bande = ± min(5 points, 25 % de la cible).
   - Actions 75 % : bande ±5 points, donc entre 70 et 80 %.
   - Crypto 15 % : bande ±3,75 points, donc entre 11,25 et 18,75 %. La crypto qui passe de 15 % à 25 % déclenche une vente jusqu'à 15 %.
   - Matières premières 10 % : bande ±2,5 points.
3. **Annuel :** chaque premier lundi de janvier, rééquilibrage complet vers les cibles, sans tenir compte des bandes.
4. **Ordre d'exécution :** les ventes d'abord, puis les achats.
5. **Limite :** un seul rééquilibrage par seuil au maximum par semaine.

## 6. Exécution des ordres

- **Montant minimum d'un ordre :** 300 € par défaut, en paramètre. En dessous, le cash attend le passage suivant pour que les frais minimums d'IBKR ne pèsent pas trop.
- **Réserve de cash :** 1 % de la valeur du portefeuille, pour payer les frais.
- **Type d'ordre :** ordre limite au prix médian ± 0,1 %, valable pour la journée, passé pendant la séance principale du marché de cotation (Xetra : 9h00-17h30, heure de Paris). Un ordre non exécuté est repassé le jour de bourse suivant.
- **Devises :** un dépôt en USD ou en GEL est converti en EUR sur IDEALPRO avant les achats.
- **Fractions :** utilisées si IBKR les autorise sur l'instrument. Sinon, la quantité est arrondie à l'unité inférieure.

## 7. Garde-fous

- **Mode par défaut :** compte paper ou simulation sans ordre réel. Le passage en réel exige un drapeau explicite `--live` **et** `mode: live` dans la configuration.
- **Réconciliation :** avant chaque passage, les positions et le cash sont relus chez IBKR, qui fait foi. Le fichier d'état local n'est qu'un journal.
- **Idempotence :** chaque décision a un identifiant (date et type de passage). Un passage déjà exécuté n'est jamais rejoué.
- **Contrôles de cohérence. On n'envoie aucun ordre, on alerte seulement, si :**
  - un prix est périmé, c'est-à-dire datant de plus de 3 jours de bourse ;
  - une ETF varie de plus de 25 % en une séance ;
  - un ordre dépasse 30 % de la valeur du portefeuille (sauf pour l'investissement initial, avec confirmation) ;
  - la somme des poids cibles s'écarte de 100 %.
- **Arrêt d'urgence :** la présence d'un fichier `STOP` dans le dossier, ou la commande Telegram `/stop`, bloque tout ordre.
- **Alerte de drawdown :** une notification part si le portefeuille perd 20 % depuis son plus haut. **Aucune vente automatique de VWCE n'est déclenchée sur drawdown.**
- **Arrêt de la poche crypto :** si son drawdown réel dépasse 1,5 fois le pire drawdown du backtest de la stratégie, la poche passe en XEON et une alerte est envoyée. La reprise se fait manuellement.

## 8. Calendrier

| Passage | Moment | Contenu |
|---|---|---|
| Quotidien | Après la clôture | Lecture des positions et du cash, investissement du cash au-dessus du seuil, contrôles |
| Hebdomadaire | Lundi matin | Signaux crypto, contrôle des bandes de rééquilibrage |
| Mensuel | Premier jour de bourse du mois | Signaux des matières premières, recalcul des poids, rapport mensuel |
| Annuel | Premier lundi de janvier | Rééquilibrage complet |

## 9. Rapport mensuel

Le rapport est envoyé par Telegram ou par e-mail et contient :

- valeur du portefeuille, versements du mois et versements cumulés ;
- performance pondérée dans le temps (hors effet des versements) sur le mois, depuis janvier et depuis le début ;
- comparaison avec un portefeuille 100 % VWCE ;
- allocation réelle face aux cibles, état de chaque signal, ordres exécutés et frais payés ;
- drawdown actuel et maximal ;
- anomalies et alertes.

En option, un appel à Haiku peut rédiger un résumé en langage naturel. C'est le seul usage de tokens, environ quelques centimes par an.

## 10. Architecture technique

- Python 3.11 et `ib_async` pour l'API IBKR.
- IB Gateway lancé et reconnecté par IBC. IBKR demande une validation 2FA à peu près une fois par semaine.
- Raspberry Pi ou petit VPS, passages planifiés avec des timers `systemd`.
- SQLite pour l'historique des passages, ordres, signaux et valeurs du portefeuille. Logs en rotation.
- Les versements sont lus via l'IBKR Flex Web Service, pour calculer la performance pondérée dans le temps.

Modules :

```
fonds/
  config.yaml        # allocation, tickers, seuils, mode paper/live
  data.py            # historique des prix (IBKR), contrôles de fraîcheur
  signals.py         # SMA + hystérésis, fonctions pures et testables
  allocation.py      # poids cibles selon les signaux et la volatilité inverse
  rebalance.py       # bandes 5/25, ventilation des versements, liste d'ordres
  broker.py          # couche IBKR (positions, cash, change, ordres)
  guards.py          # contrôles de cohérence, arrêt d'urgence, idempotence
  report.py          # rapport mensuel
  run.py             # points d'entrée daily / weekly / monthly / yearly
  backtest.py        # rejoue les mêmes règles sur l'historique
tests/
```

La logique de décision (`signals`, `allocation`, `rebalance`) est pure, sans aucun appel à IBKR. Le backtest et le mode live utilisent donc exactement le même code.

## 11. Phases et critères de passage

1. **Phase 1, maintenant :** épargne de précaution constituée, puis versements manuels ou investissement récurrent IBKR dans VWCE.
2. **Phase 2 :** orchestrateur avec VWCE, matières premières et rééquilibrage.
   - Backtest des règles depuis 2010, avec frais.
   - Tests unitaires sur les signaux et le rééquilibrage.
   - 2 mois sur le compte paper.
   - Critère pour passer en réel : aucune anomalie d'exécution, et des décisions identiques à celles du backtest sur la même période.
3. **Phase 3 :** ajout de la poche crypto, selon le même processus (backtest, puis paper, puis réel).
4. **Phase 4, en option :** carry de funding sur un exchange crypto pour le cash de la poche crypto quand elle est en OFF.

## 12. Questions ouvertes

1. Le compte IBKR a-t-il la permission crypto, ou faut-il passer par des ETP ?
2. Dans quelle devise se font les dépôts : EUR, USD ou GEL ?
3. Le rapport doit-il arriver par Telegram ou par e-mail ?
4. Le code va-t-il dans un nouveau dépôt, ou dans ce dépôt ?
5. Montant plancher du virement mensuel, à fixer une fois l'épargne de précaution constituée.
