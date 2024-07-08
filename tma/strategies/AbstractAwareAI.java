package ai.tma.strategies;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Engage;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.io.Serializable;
import java.util.*;

public abstract class AbstractAwareAI extends AbstractionLayerAI {
    public AbstractAwareAI(PathFinding a_pf) {
        super(a_pf);
    }

    public int getnHarvest() {
        return nHarvest;
    }

    public void setnHarvest(int nHarvest) {
        this.nHarvest = nHarvest;
    }

    protected int nHarvest = 2;

    public int getUnitAwareness() {
        return unitAwareness;
    }

    public void setUnitAwareness(int unitAwareness) {
        this.unitAwareness = unitAwareness;
    }

    int unitAwareness = 3;

    public int getTotBarracks() {
        return totBarracks;
    }

    public void setTotBarracks(int totBarracks) {
        this.totBarracks = totBarracks;
    }

    int totBarracks = 1;

    public double getStrategyPriority() {
        return strategyPriority;
    }

    public void setStrategyPriority(double strategyPriority) {
        this.strategyPriority = strategyPriority;
    }

    double strategyPriority = 0.0;

    public int[] getPlayerUnits() {
        return playerUnits;
    }

    public void setPlayerUnits(int[] playerUnits) {
        this.playerUnits = playerUnits;
    }

    public int[] getEnemyUnits() {
        return enemyUnits;
    }

    public void setEnemyUnits(int[] enemyUnits) {
        this.enemyUnits = enemyUnits;
    }

    int[] playerUnits = new int[] {0, 0, 0, 0, 0, 0};
    int[] enemyUnits = new int[] {0, 0, 0, 0, 0, 0};

    public int[] getUnitProduction() {
        return unitProduction;
    }

    public void setUnitProduction(int[] unitProduction) {
        this.unitProduction = unitProduction;
    }

    int[] unitProduction = new int[] {1, 1, 1};

    List<Integer> attackLine = new ArrayList<>();
    List<Integer> defenseLine = new ArrayList<>();

    private HashMap<Unit, Integer> lineMap = new HashMap<Unit, Integer>();

    protected enum PriorityMovesType{
        RETREAT,
        ENGAGE,
        ADVANCE,
        LEAVE_SPACE,
        WAIT
    }

    protected class Pos implements Serializable{
        int x;
        int y;

        Pos(int nx, int ny){
            x = nx;
            y = ny;
        }
    }

    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    UnitType lightType;
    UnitType heavyType;

    protected class Target{
        Unit unit;
        double score;
        PriorityMovesType move;

        Target(Unit u, double s, PriorityMovesType pmt){
            unit = u;
            score = s;
            move = pmt;
        }
    }

    public void reset() {
        super.reset();
    }

    public void reset(UnitTypeTable a_utt)
    {
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
    }

    protected HashMap<Unit, Target> priorityTargets = new LinkedHashMap<>();

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
//        System.out.println("LightRushAI for player " + player + " (cycle " + gs.getTime() + ")");

        List<Unit> workers = new LinkedList<>();

        for(Unit u:pgs.getUnits()) {

            //Target t;
            // behavior of bases:
            if (u.getType() == baseType &&
                    u.getPlayer() == player &&
                    gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, gs);
            }

            // behavior of melee units:
            if (u.getType().canAttack && !u.getType().canHarvest &&
                    u.getPlayer() == player &&
                    gs.getActionAssignment(u) == null) {
                /*t = GetBestTarget(gs, u, unitAwareness);
                if (t != null)
                    AddPriorityTarget(u, t);*/
                meleeUnitBehavior(u, p, gs);
            }

            // behavior of barracks:
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, gs);
            }


            // behavior of workers:
            if (u.getType().canHarvest &&
                    u.getPlayer() == player) {
                /*t = GetBestTarget(gs, u, unitAwareness);
                if (t != null)
                    AddPriorityTarget(u, t);*/
                workers.add(u);
            }


        }

        workersBehavior(workers,p,gs);

        return translateActions(player,gs);
    }

    protected Target GetPriorityTarget(Unit u){ return priorityTargets.get(u); }

    protected void RemovePriorityTarget(Unit u){ priorityTargets.remove(u); }

    protected void AddPriorityTarget(Unit u, Target target){ priorityTargets.put(u, target); }

    protected boolean HasPriorityTarget(Unit u){ return priorityTargets.containsKey(u);}

    protected List<Target> GetCloseTargets(PhysicalGameState pgs, Unit u, int d){
        List<Target> targets = new ArrayList<>();
        //PhysicalGameState pgs = gs.getPhysicalGameState();
        int distance = 0;

        for(Unit ou:pgs.getUnits()) {
            if(!ou.equals(u)){
                distance = Math.abs(ou.getX() - u.getX()) + Math.abs(ou.getY() - u.getY());
                if(distance <= d){
                    targets.add(Evaluate(u, ou, distance));
                }
            }
        }

        return targets;
    }

    protected Target GetBestTarget(PhysicalGameState pgs, Unit u, int d) {
        Target target = null;
        List<Target> targetList = GetCloseTargets(pgs, u, d);

        for(Target t:targetList){
            if(target == null)
                target = t;
            else{
                if(t.score > target.score)
                    target = t;
            }
        }

        return target;
    }

    protected Target GetBestTarget(GameState gs, Unit u, int d){
        Target target = null;
        PhysicalGameState pgs = gs.getPhysicalGameState();
        List<Target> targetList = GetCloseTargets(pgs, u, d);

        for(Target t:targetList){
            if(target == null)
                target = t;
            else{
                if(t.score > target.score)
                    target = t;
            }
        }

        return target;
    }

    public void moveTowards(Unit unit, Unit enemy, GameState gs){
        int nX, nY;
        int currDistance = Math.abs(enemy.getX() - unit.getX()) + Math.abs(enemy.getY() - unit.getY());

        nX = unit.getX() + 1;
        nY = unit.getY();

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) < currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX() - 1;
        nY = unit.getY();

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) < currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX();
        nY = unit.getY() + 1;

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) < currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX();
        nY = unit.getY() - 1;

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) < currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        attack(unit, null);
        return;
    }

    public void moveAway(Unit unit, Unit enemy, GameState gs){
        int nX, nY;
        int currDistance = Math.abs(enemy.getX() - unit.getX()) + Math.abs(enemy.getY() - unit.getY());

        nX = unit.getX() + 1;
        nY = unit.getY();

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) > currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX() - 1;
        nY = unit.getY();

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) > currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX();
        nY = unit.getY() + 1;

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) > currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        nX = unit.getX();
        nY = unit.getY() - 1;

        if(Math.abs(enemy.getX() - nX) + Math.abs(enemy.getY() - nY) > currDistance && gs.free(nX, nY)){
            move(unit, nX, nY);
            return;
        }

        attack(unit, null);
        return;
    }

    /*public void moveTowards(Unit unit, Unit other, boolean defend, GameState gs){
        int x, y, chosenPosition, width;
        List<Integer> positions = new ArrayList<>();

        width = gs.getPhysicalGameState().getWidth();

        if(lineMap.containsKey(unit)){
            chosenPosition = lineMap.get(unit);
            x = chosenPosition % width;
            y = chosenPosition / width;

            if(unit.getX() == x && unit.getY() == y){
                lineMap.remove(unit);
            }
            else
                move(unit, x, y);
            return;
        }

        if (defend){
            positions = defenseLine;

            if(positions.isEmpty()){
                //find the positions that are the most distant from other while also not being adiacent to another player unit
            }
        }

        else {
            positions = attackLine;

            if(positions.isEmpty()){
                //find the positions that are the closest from other while also not being adiacent to another player unit
            }
        }

        if(!positions.isEmpty()){
            chosenPosition = positions.remove(0);
            lineMap.put(unit, chosenPosition);
            x = chosenPosition % width;
            y = chosenPosition / width;

            move(unit, x, y);
        }
    }

    public void DefenseLine(Unit u, Unit base, int distance, PhysicalGameState pgs){
        int dX = 0, dY = 0;
        int a, b;

        if(Math.abs(base.getX() - u.getX()) + Math.abs(base.getY() - u.getY()) == distance){
            idle(u);
            return;
        }

        int[] moveX = {0, +1, 0, -1};
        int[] moveY = {+1, 0, -1, 0};

        for(int d = distance - 1; d > 0; d++){
            dX = d;
            dY = distance - d;

            for(int i = 0; i < 4; i++){
                a = base.getX() + moveX[i] * dX;
                b = base.getY() + moveY[i] * dY;

                if(a>=0 && b >=0 && a < pgs.getWidth() && b < pgs.getHeight() &&
                        pgs.getTerrain(a, b) != PhysicalGameState.TERRAIN_WALL &&
                        pgs.getUnitAt(a, b) == null){
                    move(u, a, b);
                    return;
                }
            }
        }
    }

    protected List<Pos> PositionsAtDistance(Unit u, int d, PhysicalGameState pgs){
        List<Pos> line = new ArrayList<>();
        int sX, sY, a, b, tX, tY, w, h;
        Pos p;

        sX = u.getX();
        sY = u.getY();
        a = d;
        b = 0;
        h = pgs.getHeight();
        w = pgs.getWidth();

        while (a>=0){
            tX = sX + a;
            if(tX < w){


                tY = sY + b;
                if(tY < h){
                    if(pgs.getTerrain(tX, tY) == 0 && pgs.getUnitAt(tX, tY) == null){
                        p = new Pos(tX, tY);
                        line.add(p);
                    }
                }

                tY = sY - b;
                if(tY >= 0){
                    if(pgs.getTerrain(tX, tY) == 0 && pgs.getUnitAt(tX, tY) == null){
                        p = new Pos(tX, tY);
                        line.add(p);
                    }
                }
            }

            tX = sX - a;
            if(tX >= 0){
                tY = sY + b;
                if(tY < h){
                    if(pgs.getTerrain(tX, tY) == 0 && pgs.getUnitAt(tX, tY) == null){
                        p = new Pos(tX, tY);
                        line.add(p);
                    }
                }

                tY = sY - b;
                if(tY >= 0){
                    if(pgs.getTerrain(tX, tY) == 0 && pgs.getUnitAt(tX, tY) == null){
                        p = new Pos(tX, tY);
                        line.add(p);
                    }
                }
            }

            a--;
            b++;
        }

        return line;
    }*/

    //it's better to have single evaluations on the strategies
    protected Target Evaluate(Unit u, Unit other, int d){
        double score = -1.0;
        double modifier = unitAwareness - d;
        PriorityMovesType move = PriorityMovesType.WAIT;
        //here should be a calculation of priorities based on matchup and distance
        /*if(other.getPlayer() == u.getPlayer() && u.getResources()==0){
            if(((other.getType().equals(baseType)||other.getType().equals(barracksType)) && d <= 2) || other.getType().equals(workerType) && d == 1){
                evaluateTrain = 4.0;
                move = PriorityMovesType.LEAVE_SPACE;
                return new Target(other, evaluateTrain, move);
            }
        }*/

        if(other.getPlayer()==u.getPlayer())
            return new Target(other, score, move);

        switch (u.getType().name){
            case ("Worker"):
                if(other.getType().equals(rangedType)){
                    score = 3.0;
                    move = PriorityMovesType.LEAVE_SPACE;
                    return new Target(other, score, move);
                }
                else if (other.getType().equals(baseType)||other.getType().equals(barracksType)){
                    score = 4.0;
                    move = PriorityMovesType.ENGAGE;
                    return new Target(other, score, move);
                }
                break;
            case ("Light"):
                if (other.getType().equals(rangedType)||other.getType().equals(workerType)){
                    score = 1.0 + modifier;
                    move = PriorityMovesType.ENGAGE;
                    return new Target(other, score, move);
                }
                break;
            case ("Heavy"):
                if (other.getType().equals(rangedType)||other.getType().equals(lightType)){
                    score = 1.0 + modifier;
                    move = PriorityMovesType.ENGAGE;
                    return new Target(other, score, move);
                }
                break;
            case ("Ranged"):
                if (other.getType().equals(rangedType)||other.getType().equals(workerType)){
                    score = 1.0 + modifier;
                    move = PriorityMovesType.ENGAGE;
                    return new Target(other, score, move);
                }
                if (other.getType().equals(heavyType)||other.getType().equals(lightType)){
                    if(d <= 2){
                        score = 6.0;
                        move = PriorityMovesType.LEAVE_SPACE;
                        return new Target(other, score, move);
                    }
                    else{
                        score = 2.0;
                        move = PriorityMovesType.ENGAGE;
                        return new Target(other, score, move);
                    }
                }
                break;
            default:
                break;
        }

        return new Target(other, score, move);
    }

    protected void PriorityMove(Unit unit, Target target, GameState gs){
        switch (target.move){
            case ENGAGE -> {
                attack(unit, target.unit);
                break;
            }

            case RETREAT -> {
                break;
            }

            case LEAVE_SPACE -> {
                moveAway(unit, target.unit, gs);
                break;
            }

            case WAIT -> {
                break;
            }
        }
    }

    public int realDistance(Unit u1, Unit u2, GameState gs) {
        AStarPathFinding aStar = new AStarPathFinding();

        PhysicalGameState pgs = gs.getPhysicalGameState();
        int d = aStar.findDistToPositionInRange(u1, u2.getPosition(pgs), 1, gs, gs.getResourceUsage());

        if(d == -1)
            return 9999;
        else
            return d;
    }

    public void engage(Unit u, Unit target) {
        actions.put(u, new Engage(u, target, pf));
    }

    protected abstract void workersBehavior(List<Unit> workers, Player p, GameState gs);
    protected abstract void barracksBehavior(Unit u, Player p, GameState gs);
    protected abstract void baseBehavior(Unit u, Player p, GameState gs);
    protected abstract void meleeUnitBehavior(Unit u, Player p, GameState gs);
}
