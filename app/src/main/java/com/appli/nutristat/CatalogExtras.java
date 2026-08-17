package com.appli.nutristat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Compléments de catalogue génériques (sans marques). */
public final class CatalogExtras {
 private CatalogExtras(){}
 public static void apply(NutriDb db){SQLiteDatabase d=db.getWritableDatabase();renameLegacy(d);seedFoods(db);seedRecipes(db);}
 private static void rename(SQLiteDatabase d,String oldName,String newName){try{Cursor c=d.rawQuery("select id from foods where lower(name)=lower(?)",new String[]{newName});boolean exists=c.moveToFirst();c.close();if(!exists){ContentValues v=new ContentValues();v.put("name",newName);d.update("foods",v,"name=?",new String[]{oldName});}}catch(Exception ignored){}}
 private static void renameLegacy(SQLiteDatabase d){
  rename(d,"Coca-Cola","Soda cola");rename(d,"Glace Magnum Classic","Bâtonnet glacé vanille-chocolat");rename(d,"Glace à l'eau type Yeti","Glace à l'eau en tube");
  rename(d,"Bœuf extra maigre","Steak de bœuf maigre");rename(d,"Pâtes blanches cuites","Pâtes cuites");rename(d,"Semoule cuite","Semoule couscous cuite");
 }
 private static void add(NutriDb db,String name,String cats,String basis,String unit,double serving,String label,double kcal,double p,double fat,double carbs,double fiber,double sugar){if(db.foodByName(name)!=null)return;NutriDb.Food f=new NutriDb.Food();f.name=name;f.brand="";f.categories=cats;f.basisUnit=basis;f.defaultUnit=unit;f.servingBase=serving;f.servingLabel=label;f.cal=kcal;f.protein=p;f.fat=fat;f.carbs=carbs;f.fiber=fiber;f.sugar=sugar;f.builtin=1;f.userModified=0;db.saveFood(f,false);}
 private static void seedFoods(NutriDb db){
  add(db,"Farine de blé T45","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",350,10.0,1.0,72.0,3.0,1.0);
  add(db,"Farine de blé T55","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",348,9.8,1.0,72.0,3.2,1.0);
  add(db,"Farine de blé T65","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",345,10.2,1.2,70.5,3.5,1.0);
  add(db,"Farine de blé T80","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",340,10.5,1.5,68.0,5.0,1.0);
  add(db,"Farine de blé T110","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",335,11.0,1.8,65.0,7.0,1.0);
  add(db,"Farine de blé complète T150","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",332,11.5,2.0,62.0,10.0,1.0);
  add(db,"Farine de sarrasin","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",343,13.0,3.4,62.0,10.0,1.0);
  add(db,"Farine de maïs","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",361,6.9,3.9,76.9,7.3,1.0);
  add(db,"Farine de seigle","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",335,9.0,1.6,68.0,13.0,1.0);
  add(db,"Farine d'épeautre","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",338,14.6,2.4,59.5,10.7,1.0);
  add(db,"Farine de riz","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",366,6.0,1.4,80.0,2.4,0.2);
  add(db,"Farine de châtaigne","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",369,6.0,3.7,78.0,7.0,24.0);
  add(db,"Farine de pois chiche","Farines,Féculents,Légumineuses","g","g",30,"2 c. à soupe ≈ 30 g",387,22.0,6.7,58.0,11.0,11.0);
  add(db,"Farine d'avoine","Farines,Féculents","g","g",30,"2 c. à soupe ≈ 30 g",404,14.7,9.1,65.7,6.5,1.0);
  add(db,"Fécule de maïs","Farines,Féculents","g","g",20,"1 c. à soupe bombée ≈ 20 g",381,0.3,0.1,91.0,0.9,0.0);
  add(db,"Baguette","Féculents,Pains","g","g",60,"1 morceau ≈ 60 g",279,8.5,1.2,57.0,3.0,2.5);
  add(db,"Pain de mie","Féculents,Pains","g","g",35,"1 tranche ≈ 35 g",265,8.5,4.0,48.0,3.5,5.0);
  add(db,"Flocons d'avoine","Féculents,Céréales","g","g",40,"1 portion ≈ 40 g",379,13.2,6.5,67.7,10.1,1.0);
  add(db,"Blé cuit","Féculents,Céréales","g","g",150,"1 portion cuite ≈ 150 g",127,4.5,0.8,25.0,3.5,0.5);
  add(db,"Polenta cuite","Féculents,Céréales","g","g",150,"1 portion cuite ≈ 150 g",85,1.8,0.4,18.0,1.0,0.2);
  add(db,"Gnocchis cuits","Féculents","g","g",180,"1 portion ≈ 180 g",150,4.0,1.0,31.0,2.0,1.0);
  add(db,"Tagliatelles cuites","Féculents","g","g",180,"1 portion cuite ≈ 180 g",158,5.8,0.9,31.0,1.2,0.6);
  add(db,"Coquillettes cuites","Féculents","g","g",180,"1 portion cuite ≈ 180 g",158,5.8,0.9,31.0,1.2,0.6);
  add(db,"Spaghetti cuits","Féculents","g","g",180,"1 portion cuite ≈ 180 g",158,5.8,0.9,31.0,1.2,0.6);
  add(db,"Penne cuites","Féculents","g","g",180,"1 portion cuite ≈ 180 g",158,5.8,0.9,31.0,1.2,0.6);
  add(db,"Riz blanc cuit","Féculents","g","g",150,"1 portion cuite ≈ 150 g",130,2.7,0.3,28.0,0.4,0.1);
  add(db,"Purée de pomme de terre","Féculents","g","g",200,"1 portion ≈ 200 g",90,2.0,3.0,14.0,1.3,1.0);
  add(db,"Frites","Féculents","g","g",150,"1 portion ≈ 150 g",312,3.4,15.0,41.0,3.8,0.3);
  add(db,"Flageolets cuits","Légumineuses,Féculents","g","g",150,"1 portion ≈ 150 g",85,6.0,0.4,13.0,6.0,0.5);
  add(db,"Haricots blancs cuits","Légumineuses,Féculents","g","g",150,"1 portion ≈ 150 g",110,7.3,0.5,18.0,6.3,0.4);
  add(db,"Champignon de Paris","Légumes","g","g",100,"1 portion ≈ 100 g",22,3.1,0.3,3.3,1.0,2.0);
  add(db,"Poireau","Légumes","g","g",150,"1 poireau moyen ≈ 150 g",31,1.5,0.3,7.0,2.8,3.9);
  add(db,"Chou-fleur","Légumes","g","g",150,"1 portion ≈ 150 g",25,1.9,0.3,5.0,2.0,1.9);
  add(db,"Radis rose","Légumes,Crudités","g","g",80,"1 petite botte ≈ 80 g",16,0.7,0.1,3.4,1.6,1.9);
  add(db,"Échalote","Légumes","g","g",30,"1 échalote ≈ 30 g",72,2.5,0.1,16.8,3.2,7.9);
  add(db,"Betterave cuite","Légumes,Crudités","g","g",100,"1 portion ≈ 100 g",44,1.7,0.2,10.0,2.0,7.0);
  add(db,"Tomates cerises","Légumes,Crudités","g","g",100,"8 à 10 tomates ≈ 100 g",18,0.9,0.2,3.9,1.2,2.6);
  add(db,"Maïs doux","Légumes,Féculents","g","g",100,"1 petite portion ≈ 100 g",86,3.2,1.2,19.0,2.7,6.3);
  add(db,"Abricot","Fruits","g","portion",50,"1 abricot ≈ 50 g",48,1.4,0.4,11.1,2.0,9.2);
  add(db,"Melon","Fruits","g","g",180,"1 tranche ≈ 180 g",34,0.8,0.2,8.2,0.9,7.9);
  add(db,"Raisin","Fruits","g","g",125,"1 petite grappe ≈ 125 g",69,0.7,0.2,18.1,0.9,15.5);
  add(db,"Kiwi","Fruits","g","portion",90,"1 kiwi ≈ 90 g",61,1.1,0.5,14.7,3.0,9.0);
  add(db,"Ananas","Fruits","g","g",150,"2 tranches ≈ 150 g",50,0.5,0.1,13.1,1.4,9.9);
  add(db,"Pamplemousse rose","Fruits","g","portion",230,"1/2 gros fruit ≈ 230 g",42,0.8,0.1,10.7,1.6,7.0);
  add(db,"Nectarine","Fruits","g","portion",140,"1 nectarine ≈ 140 g",44,1.1,0.3,10.6,1.7,7.9);
  add(db,"Cerise","Fruits","g","g",100,"15 à 20 cerises ≈ 100 g",63,1.1,0.2,16.0,2.1,12.8);
  add(db,"Prune","Fruits","g","portion",70,"1 prune ≈ 70 g",46,0.7,0.3,11.4,1.4,9.9);
  add(db,"Lait entier","Produits laitiers,Boissons","ml","cL",250,"1 verre ≈ 25 cL",64,3.3,3.6,4.8,0,4.8);
  add(db,"Brie","Produits laitiers,Fromages","g","g",30,"1 portion ≈ 30 g",334,20.8,27.7,0.5,0,0.5);
  add(db,"Comté","Produits laitiers,Fromages","g","g",30,"1 portion ≈ 30 g",410,27.2,33.3,0.4,0,0.4);
  add(db,"Camembert","Produits laitiers,Fromages","g","g",30,"1 portion ≈ 30 g",300,20.0,24.0,0.5,0,0.5);
  add(db,"Feta","Produits laitiers,Fromages","g","g",40,"1 portion ≈ 40 g",265,14.2,21.5,4.1,0,4.1);
  add(db,"Emmental râpé","Produits laitiers,Fromages","g","g",30,"1 petite poignée ≈ 30 g",380,27.0,28.0,1.0,0,0.5);
  add(db,"Fromage râpé","Produits laitiers,Fromages","g","g",30,"1 petite poignée ≈ 30 g",380,27.0,28.0,1.0,0,0.5);
  add(db,"Yaourt grec nature","Produits laitiers,Yaourts","g","portion",150,"1 pot ≈ 150 g",120,6.0,8.0,5.0,0,4.5);
  add(db,"Yaourt nature sucré","Produits laitiers,Yaourts","g","portion",125,"1 pot ≈ 125 g",95,3.5,3.0,13.5,0,13.0);
  add(db,"Yaourt aux fruits","Produits laitiers,Yaourts","g","portion",125,"1 pot ≈ 125 g",100,3.5,2.5,16.0,0.5,14.0);
  add(db,"Yaourt à boire","Produits laitiers,Boissons,Goûter","ml","cL",200,"1 petite bouteille ≈ 20 cL",75,3.0,1.5,12.5,0,12.0);
  add(db,"Dessert soja nature","Desserts,Produits végétaux","g","portion",100,"1 pot ≈ 100 g",54,4.0,2.3,3.5,0.6,2.5);
  add(db,"Crème dessert chocolat","Desserts","g","portion",125,"1 pot ≈ 125 g",130,3.0,4.0,20.0,1.0,17.0);
  add(db,"Crème dessert vanille","Desserts","g","portion",125,"1 pot ≈ 125 g",120,3.0,3.5,19.0,0,16.0);
  add(db,"Flan caramel","Desserts","g","portion",100,"1 pot ≈ 100 g",125,4.0,3.0,20.0,0,17.0);
  add(db,"Riz au lait","Desserts","g","portion",125,"1 pot ≈ 125 g",125,3.3,3.2,20.0,0.3,13.0);
  add(db,"Compote de pommes sans sucres ajoutés","Desserts,Fruits","g","portion",100,"1 pot ≈ 100 g",50,0.2,0.1,12.0,1.5,10.0);
  add(db,"Brioche","Goûter,Féculents","g","g",50,"1 tranche ≈ 50 g",375,8.0,15.0,52.0,2.0,14.0);
  add(db,"Pain au lait","Goûter,Féculents","g","portion",35,"1 pain au lait ≈ 35 g",360,9.0,12.0,54.0,2.0,13.0);
  add(db,"Biscuit fourré chocolat","Goûter,Desserts","g","portion",25,"1 biscuit ≈ 25 g",480,6.0,20.0,69.0,3.0,35.0);
  add(db,"Barre chocolatée lait","Goûter,Desserts","g","portion",25,"1 petite barre ≈ 25 g",550,8.0,34.0,53.0,2.0,47.0);
  add(db,"Bonbon gélifié","Goûter,Desserts","g","g",30,"1 petite poignée ≈ 30 g",340,5.0,0.5,78.0,0,58.0);
  add(db,"Soda cola","Boissons","ml","cL",200,"1 verre ≈ 20 cL",42,0,0,10.6,0,10.6);
  add(db,"Soda cola sans sucres","Boissons","ml","cL",200,"1 verre ≈ 20 cL",0.5,0,0,0.1,0,0);
  add(db,"Eau plate","Boissons","ml","cL",250,"1 verre ≈ 25 cL",0,0,0,0,0,0);
  add(db,"Eau gazeuse","Boissons","ml","cL",250,"1 verre ≈ 25 cL",0,0,0,0,0,0);
  add(db,"Thé glacé pêche","Boissons","ml","cL",250,"1 verre ≈ 25 cL",27,0,0,6.5,0,6.3);
  add(db,"Boisson aux fruits tropicale","Boissons","ml","cL",250,"1 verre ≈ 25 cL",43,0.1,0,10.5,0,10.0);
  add(db,"Tonic","Boissons","ml","cL",200,"1 verre ≈ 20 cL",34,0,0,8.5,0,8.5);
  add(db,"Jus de pomme","Boissons","ml","cL",200,"1 verre ≈ 20 cL",46,0.1,0.1,11.3,0.2,10.0);
  add(db,"Smoothie fruits","Boissons,Fruits","ml","cL",250,"1 bouteille/verre ≈ 25 cL",60,0.6,0.3,14.0,1.2,12.0);
  add(db,"Escalope de poulet","Viandes","g","g",150,"1 escalope ≈ 150 g",110,23.1,1.2,0,0,0);
  add(db,"Steak haché 15%","Viandes","g","g",125,"1 steak ≈ 125 g",215,19.0,15.0,0,0,0);
  add(db,"Côte de porc","Viandes","g","g",180,"1 côte ≈ 180 g",242,21.0,17.0,0,0,0);
  add(db,"Saucisse de Toulouse","Viandes","g","g",100,"1 saucisse ≈ 100 g",300,16.0,26.0,1.0,0,0.5);
  add(db,"Saucisse de Strasbourg","Viandes","g","portion",50,"1 saucisse ≈ 50 g",280,12.0,25.0,2.0,0,1.0);
  add(db,"Pâté de campagne","Apéritifs,Viandes","g","g",30,"1 tranche ≈ 30 g",330,14.0,29.0,3.0,0,1.0);
  add(db,"Chair à saucisse","Viandes","g","g",125,"1 portion ≈ 125 g",290,16.0,24.0,2.0,0,1.0);
  add(db,"Cordon bleu","Viandes,Plats préparés","g","portion",100,"1 pièce ≈ 100 g",235,15.0,13.0,15.0,1.0,1.5);
  add(db,"Filet de dorade","Poissons & fruits de mer","g","g",150,"1 filet ≈ 150 g",105,20.0,2.5,0,0,0);
  add(db,"Filet de bar","Poissons & fruits de mer","g","g",150,"1 filet ≈ 150 g",124,20.0,4.5,0,0,0);
  add(db,"Sole","Poissons & fruits de mer","g","g",150,"1 filet ≈ 150 g",86,18.0,1.2,0,0,0);
  add(db,"Noix de Saint-Jacques","Poissons & fruits de mer","g","g",100,"1 portion ≈ 100 g",88,17.0,0.8,3.0,0,0);
  add(db,"Moules cuites","Poissons & fruits de mer","g","g",150,"1 portion décoquillée ≈ 150 g",172,24.0,4.5,7.0,0,0);
  add(db,"Anchois à l'huile","Poissons & fruits de mer","g","g",20,"5 filets ≈ 20 g",210,29.0,10.0,0,0,0);
  add(db,"Poisson pané en bâtonnets","Poissons & fruits de mer,Plats préparés","g","portion",90,"4 à 5 bâtonnets ≈ 90 g",220,12.0,10.0,20.0,1.0,1.0);
  add(db,"Colin pané","Poissons & fruits de mer,Plats préparés","g","portion",100,"1 filet pané ≈ 100 g",210,14.0,9.0,18.0,1.2,1.0);
  add(db,"Saumon pané","Poissons & fruits de mer,Plats préparés","g","portion",100,"1 filet pané ≈ 100 g",250,15.0,15.0,15.0,1.0,1.0);
  add(db,"Pesto","Sauces","g","g",30,"2 c. à soupe ≈ 30 g",470,5.0,46.0,8.0,2.0,3.0);
  add(db,"Pâte brisée","Féculents,Pâtisserie","g","g",230,"1 pâte ronde ≈ 230 g",400,6.0,22.0,45.0,2.0,2.0);
 }
 private static NutriDb.Ingredient ing(NutriDb db,String food,double grams){NutriDb.Food f=db.foodByName(food);if(f==null)return null;NutriDb.Ingredient i=new NutriDb.Ingredient();i.food=f;i.foodId=f.id;i.foodName=f.name;i.grams=grams;return i;}
 private static void recipe(NutriDb db,String name,String cat,double portions,Object[][] specs){for(NutriDb.Recipe r:db.recipes())if(r.name.equalsIgnoreCase(name))return;ArrayList<NutriDb.Ingredient> list=new ArrayList<>();for(Object[] s:specs){NutriDb.Ingredient i=ing(db,(String)s[0],((Number)s[1]).doubleValue());if(i!=null)list.add(i);}if(list.size()!=specs.length)return;NutriDb.Recipe r=new NutriDb.Recipe();r.name=name;r.categories=cat;r.portions=portions;r.builtin=1;r.userModified=0;db.saveRecipe(r,list);}
 private static void seedRecipes(NutriDb db){
  recipe(db,"Gratin de coquillettes au jambon","Plats",4,new Object[][]{{"Coquillettes cuites",720},{"Jambon blanc",180},{"Crème fraîche entière",150},{"Emmental râpé",120}});
  recipe(db,"Pâtes au thon et à la tomate","Plats",4,new Object[][]{{"Pâtes cuites",720},{"Thon au naturel",280},{"Sauce tomate",400},{"Huile d'olive",10}});
  recipe(db,"Pâtes au pesto et tomates cerises","Plats",4,new Object[][]{{"Pâtes cuites",720},{"Pesto",120},{"Tomates cerises",300},{"Parmesan",40}});
  recipe(db,"Pâtes au saumon et à la crème","Plats",4,new Object[][]{{"Pâtes cuites",720},{"Saumon",500},{"Crème fraîche entière",180}});
  recipe(db,"Pâtes aux champignons et à la crème","Plats",4,new Object[][]{{"Pâtes cuites",720},{"Champignon de Paris",400},{"Crème fraîche entière",180},{"Parmesan",40}});
  recipe(db,"Salade de pâtes estivale","Plats",4,new Object[][]{{"Pâtes cuites",600},{"Tomates cerises",300},{"Feta",160},{"Olives vertes",60},{"Thon au naturel",140},{"Huile d'olive",20}});
  recipe(db,"One pot pasta au poulet","Plats",4,new Object[][]{{"Pâtes cuites",720},{"Blanc de poulet",500},{"Tomate",300},{"Oignon",100},{"Crème fraîche entière",100}});
  recipe(db,"Blanquette de veau et riz","Plats",4,new Object[][]{{"Veau",600},{"Riz blanc cuit",600},{"Crème fraîche entière",150},{"Carotte",200},{"Champignon de Paris",200}});
  recipe(db,"Poulet rôti et pommes de terre","Plats",4,new Object[][]{{"Blanc de poulet",600},{"Pomme de terre cuite",800},{"Huile d'olive",20}});
  recipe(db,"Steak haché et frites","Plats",1,new Object[][]{{"Steak haché 15%",125},{"Frites",150}});
  recipe(db,"Galette sarrasin jambon œuf fromage","Plats",1,new Object[][]{{"Farine de sarrasin",70},{"Œuf entier",60},{"Jambon blanc",45},{"Emmental râpé",30}});
  recipe(db,"Tomates farcies","Plats",4,new Object[][]{{"Tomate",800},{"Chair à saucisse",500},{"Oignon",100},{"Riz blanc cuit",200}});
  recipe(db,"Cordon bleu et purée","Plats",1,new Object[][]{{"Cordon bleu",100},{"Purée de pomme de terre",200}});
 }
}
