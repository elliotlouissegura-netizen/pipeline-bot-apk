# Fonds personnel autonome — Spécification de l'orchestrateur

Statut : **brouillon à valider** · Version 0.2 · 2026-10-20

## 1. Objectif

Un fonds personnel qui s'exécute seul sur un compte IBKR :

- l'utilisateur fait un virement mensuel (montant variable, avec un plancher) ;
- l'orchestrateur investit, rééquilibre, applique les règles de tendance et envoie un rapport ;
- objectif de long terme : environ 8 à 9 %/an au centre, 10 %+ dans un scénario favorable. Ce n'est pas une garantie ;
- perte temporaire maximale possible : -45 à -60 %, à cause des 40 % de choix personnels concentrés.

Contraintes de l'utilisateur :

- pas d'obligations ;
- matières premières seulement quand leur tendance est haussière ;
- préférence pour les actions et la crypto ;
- résident fiscal géorgien, à confirmer, donc pas de contrainte fiscale sur les ventes ;
- aucun appel à un LLM pendant le fonctionnement : tout est du code déterministe.

## 2. Univers et allocation cible

Répartition : 60 % de cœur diversifié, 40 % de choix personnels (20 % crypto, 20 % actions).

| Poche | Ligne | Cible | Instrument (à confirmer selon la dispo IBKR) | Signal |
|---|---|---|---|---|
| Cœur | Actions monde | 55 % | VWCE (Vanguard FTSE All-World Acc, Xetra, EUR) | Aucun |
| Cœur | Matières premières | 5 % | IGLN (or), SSLN (argent), CMOD (panier Bloomberg Commodity) | Mensuel |
| Choix crypto | Ether | 5 % | ETP Ether physique (Europe) | Hebdo |
| Choix crypto | Solana | 5 % | ETP Solana physique (Europe) | Hebdo |
| Choix crypto | Hyperliquid | 5 % | 21Shares Hyperliquid ETP (CH1471826029, SIX, frais 2,5 %/an) ou HYPE en direct sur un exchange | Hebdo |
| Choix crypto | 4e ligne | 5 % | **À confirmer** : proposition = 5 % de plus sur ETH (donc ETH à 10 %), alternative = LINK | Hebdo |
| Choix actions | Strategy (proxy Bitcoin à effet de levier) | 5 % | MSTR (Nasdaq, USD) | Hebdo, comme une crypto |
| Choix actions | Xiaomi | 5 % | 1810 (HKEX, lots de 200 actions), ou ADR XIACY si disponible | Mensuel |
| Choix actions | Écosystème Musk | 5 % | TSLA 2,5 % et SPCX (SpaceX) 2,5 % | Mensuel |
| Choix actions | 4e ligne | 5 % | **À confirmer** : proposition = TSM (TSMC, ADR) | Mensuel |
| Parking | — | reste | XEON (monétaire €STR) | — |

- Tous les tickers et poids sont des paramètres de `config.yaml`. Aucun n'est codé en dur.
- **Plafond des choix personnels :** 40 % du portefeuille au total, et 8 % maximum par ligne avant rééquilibrage forcé.
- **Changer une ligne choisie :** le changement est pris en compte au passage mensuel suivant, jamais immédiatement.
- **Choix crypto éligibles :** top 50 par capitalisation, et un instrument liquide (ETP ou exchange) est disponible.

## 3. Signaux de tendance

On vend parce que la hausse s'arrête, jamais parce qu'elle « a assez monté ».

| Actif | Fréquence d'évaluation | Signal ON | Signal OFF |
|---|---|---|---|
| Cryptos et MSTR | Hebdomadaire (clôture du vendredi) | Clôture > SMA 20 semaines × (1 + b) | Clôture < SMA 20 semaines × (1 − b) |
| Actions choisies (sauf MSTR) | Mensuelle (dernière clôture du mois) | Clôture > SMA 10 mois × (1 + b) | Clôture < SMA 10 mois × (1 − b) |
| Or, argent, panier | Mensuelle | Clôture > SMA 10 mois × (1 + b) | Clôture < SMA 10 mois × (1 − b) |

- `b` est une marge d'hystérésis, à 1 % par défaut. Entre les deux seuils, le signal garde son état précédent, ce qui évite les allers-retours inutiles.
- Les prix utilisés sont ceux de l'instrument lui-même, téléchargés depuis IBKR. Si l'historique est trop court, on utilise l'actif sous-jacent (par exemple le token pour un ETP récent). Pour une action introduite récemment en bourse (SPCX), le signal s'applique dès qu'il y a 10 mois d'historique ; avant, la ligne est détenue sans signal.
- VWCE n'a **aucun** signal : on ne fait pas de market timing sur le cœur du portefeuille.

## 4. Poids et destination quand un signal est OFF

- **Crypto :** chaque ligne ON reçoit son poids cible. Une ligne OFF part en XEON.
- **Actions choisies :** chaque ligne ON reçoit son poids cible. Une ligne OFF part en **VWCE**, jusqu'au retour de la tendance ou au remplacement de la ligne.
- **MSTR :** traité comme une crypto. OFF → XEON.
- **Matières premières :** chaque actif ON reçoit un poids proportionnel à `1 / volatilité`, la volatilité étant calculée sur 63 jours de rendements quotidiens. La somme est ramenée à 5 % et chaque actif est plafonné à 2,5 %. Un actif OFF part en XEON.
  - On ne redistribue pas le poids d'une ligne OFF vers les autres lignes de sa poche. Une poche ne doit pas se concentrer sur un seul actif.
- **Actions en lots (Xiaomi sur HKEX) :** on n'achète un lot que si le retard sur la cible vaut au moins un lot entier. Tant que le portefeuille est petit, la ligne peut rester sous sa cible ; le cash correspondant attend en XEON.

## 5. Rééquilibrage (la prise de profit automatique)

On vend ce qui a monté et on rachète ce qui a baissé, mécaniquement.

1. **Versements :** le cash disponible achète d'abord les lignes les plus en retard sur leur cible, sans jamais vendre. C'est le mécanisme principal.
2. **Seuil :** le contrôle se fait chaque semaine. Une ligne est rééquilibrée vers sa cible si elle sort de sa bande de tolérance, selon la règle « 5/25 » : bande = ± min(5 points, 25 % de la cible).
   - VWCE 55 % : bande ±5 points, donc entre 50 et 60 %.
   - Chaque ligne choisie à 5 % : bande ±1,25 point, donc entre 3,75 et 6,25 %. Si MSTR ou SOL flambe et passe à 6,5 %, l'excédent est revendu jusqu'à 5 %.
   - Total des choix personnels : jamais au-dessus de 40 % après rééquilibrage.
   - Matières premières 5 % : bande ±1,25 point.
   - Une vente de rééquilibrage plus petite que le montant minimum d'un ordre attend le passage suivant.
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

1. Le compte IBKR a-t-il la permission crypto, ou faut-il passer par des ETP ? Pour HYPE, ETP à 2,5 %/an de frais ou compte sur un exchange ?
2. 4e ligne crypto : 5 % de plus sur ETH, ou LINK ? 4e ligne action : TSMC, ou autre ?
3. Dans quelle devise se font les dépôts : EUR, USD ou GEL ?
4. Le rapport doit-il arriver par Telegram ou par e-mail ?
5. Le code va-t-il dans un nouveau dépôt, ou dans ce dépôt ?
6. Montant plancher du virement mensuel, à fixer une fois l'épargne de précaution constituée.
