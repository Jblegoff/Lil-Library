# Lil-Library
Projet par Jean-Baptiste Le Goff 4AFSI2

## Présentation
Application Android en JAVA utilisant l'architerchture MVC. l'application utilise l'api "MangaEden API". Elle permet de chercher et lire (en anglais) les manga disponible sur le site https://www.mangaeden.com 

## Outils de dévelopement

- IDE:Android Studio
- Utilisation de la librairie Retrofit2 pour effectuer les appels au API REST

## Consigne 

- 3 activités et 2 fragments
- Utilisation de RecyclerView
- Appel webservices à une API REST : <a href="https://www.mangaeden.com/api">MangaEden Api</a>
- Architecture MVC
- GitFlow

## Fonctionnalité 
affiche les posters des manga disponible sur https://www.mangaeden.com et permet d'avoir des details (synopsis,auteur,etc..) et de lire les chapitre présents sur le site en cliquant dessus

<img src="img_demo/App_how_to_use_1.gif" width="300" alt="how_to_use"> 

### Splash Screen
Un écran pendant le démarage de l'application

<img src="img_demo/Splash_screen.jpg" alt="splash" width="300">

### Home Screen
Le menu d'acceuil de l'application, permet d'accéder à l'ensemble des manga présent sur le site via une <i>Recycler View</i>

<img src="img_demo/home.jpg" alt="home" width="300">

### Details Screen
Deux écrans montrant les détails du manga ainsi que les chapitres disponibles. La lecure d'un chapitre et la presentation en onglet de la partie détails/chapitre disponible sont faites grâçe à des <i>Fragments</i> 

<img src="img_demo/detail_1.jpg" atl="detail1" width="300"> <img src="img_demo/detail_2.jpg" alt="details2" width="300">

### Search Screen
L'ecran de résultat lors q'une recherche (ici avec la recherche "captain")

<img src="img_demo/search.jpg" alt="search1" width="300"> <img src="img_demo/App_search_1.gif" width="300" alt="search"> 

## Amélioration possible

- Ajout d'un systeme de login pour enregistrer les manga que l'on veut suivre
- Notification à la sortie d'un manga suivi

Note:le site MangaEden.com recensant tout type de manga il peut être possible d'avoir des manga pouvant heurter la sensibilté
de certaine personne
