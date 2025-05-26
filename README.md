Hi!

This is my game for the final project.

Quick guide:

This is a slightly simplified version of the "Battleships" tabletop game.
The main differences are: the ships are only straight lines, it doesn't include the aircraft carrier ship shape, as in the original game.
I implemented a simple AI instead of a second human player, since I didn't know how to make it so that the other player cannot see your ships (also genuinely seems like more of a hassle than to implement an AI).

Gameplay steps:

1. Place ships on your grid (use the button near the top to change ship placement direction).
2. The player goes first, choose a tile on the enemy's grid to reveal if there is part of a ship in that tile.
3. On a succesful hit: the player gets to go again, and choose another tile to hit. Continue until a miss occurs.
   NOTE: the top of the screen will update with the "Sunk!" message to indicate that you have sunk an entire ship so that you don't keep firing in the same direction for no reason.
4. On a miss: your turn ends, the opponent gets to attack.
5. End condition: Once all of the ship tiles on the enemy's board have been hit, the game ends, and you win (and vice versa).
6. The game then can be restarted using the restart button, that will appear on the bottom of the screen.

Known bugs: 
If hitting more than two ship tiles in a row, they will only update once a miss occurs (for the AI, it means there won't be a delay between subsequent attacks until a miss occurs)



AI usage:
I used xAI's GROK to assist with error fixing and help with the AI player's behavior (i probably could have implemented this myself, but, was running out of time, sorry).

Prompts:

Prompt 1
I opened logcat for my Kotlin project and then tried to launch my app, this is the error log I got:
---------------------------- PROCESS STARTED (7952) for package com.example.battleships ----------------------------
2025-05-26 21:54:32.294  7952-7952  ziparchive              com.example.battleships              W  Unable to open '/data/app/~~uHYFVQJfCIgws32-Tf-1sg==/com.example.battleships-qfCYVDkpekvA5s2H9hMG6w==/base.dm': No such file or directory
(..)
com.android.internal.os.ZygoteInit.main(ZygoteInit.java:886) 
---------------------------- PROCESS ENDED (7952) for package com.example.battleships ----------------------------

(didn't post the whole thing here, it was very long, can provide if needed)
(The issue was with the app/build.gradle.kts file, i had improperly added kapt)

Prompt 2
I was finally able to launch the game, but there is a problem that as soon as I hit one tile of the enemy's ships, the game instantly ends.


Prompt 3
I want to improve the AI of my battleships game. Currently, the AI completely blindly searches the entire board. 
Once it has hit a player's ship, the next fire attempts should be in the tiles surrounding the place of the hit (excluding diagonally). Could you please show me an implementation of this behavior?


P.S. The initial commit already contains most of the game logic, as I had previously started working on the file, but forgot to create a repository.
