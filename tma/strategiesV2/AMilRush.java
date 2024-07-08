package ai.tma.strategiesV2;

import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AMilRush extends AwareAI{

    public AMilRush(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public AMilRush(UnitTypeTable a_utt){
        this(a_utt, new AStarPathFinding());
    }

    @Override
    public AI clone() {
        return new AMilRush(utt, pf);
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return null;
    }

    @Override
    protected UnitAction workerBehavior(Unit worker, Player p, GameState gs, ResourceUsage ru) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestBase = null;
        Unit closestResource = null;
        int closestDistance = 0;
        for(Unit u2:pgs.getUnits()) {
            if (u2.getType().isResource) {
                int d = Math.abs(u2.getX() - worker.getX()) + Math.abs(u2.getY() - worker.getY());
                if (closestResource==null || d<closestDistance) {
                    closestResource = u2;
                    closestDistance = d;
                }
            }
        }
        closestDistance = 0;
        for(Unit u2:pgs.getUnits()) {
            if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                int d = Math.abs(u2.getX() - worker.getX()) + Math.abs(u2.getY() - worker.getY());
                if (closestBase==null || d<closestDistance) {
                    closestBase = u2;
                    closestDistance = d;
                }
            }
        }

        if(closestBase!=null||closestResource!=null)
            return Harvest(worker, closestResource, closestBase, gs, ru);

        return scoutBehavior(worker, p, gs, ru);
    }

    @Override
    protected UnitAction barracksBehavior(Unit u, Player p, GameState gs) {
        int unit = 0, value = 0;
        UnitType type = heavyType;
        UnitType[] types = new UnitType[] {lightType, heavyType, rangedType};
        for(int i = 0; i < unitProduction.length; i++){
            if(unitProduction[i] > value){
                value = unitProduction[i];
                unit = i;
                type = types[i];
            }
        }

        if(p.getResources() >= type.cost)
            return Train(u, type, gs);

        return null;
    }

    @Override
    protected UnitAction baseBehavior(Unit u, Player p, GameState gs) {
        if ((p.getResources()>=workerType.cost && playerUnits[0] < nHarvest) ||
                ((p.getResources()>=workerType.cost && p.getResources() > resourceTreshold)))
            return Train(u, workerType, gs);
        return null;
    }

    @Override
    protected UnitAction meleeUnitBehavior(Unit u, Player p, GameState gs, ResourceUsage ru) {
        if(u.getType()==rangedType)
            return rangedBehavior(u, p, gs, ru);
        if(u.getType()==heavyType || u.getType()==lightType)
            return aggroMeleeBehevior(u, p, gs, ru);
        return Attack(u,null,gs,ru);
        /*Unit closestEnemy = null;
        Unit closestMeleeEnemy = null;
        int closestDistance = 0;
        int enemyDistance = 0;
        int mybase = 0;
        PhysicalGameState pgs = gs.getPhysicalGameState();

        for(Unit u2:pgs.getUnits()) {
            if (u2.getPlayer()>=0 && u2.getPlayer()!=p.getID()) {
                int d = evaluateAttackTarget(u, u2, pgs);
                if (closestEnemy==null || d<closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy!=null) {
            return Attack(u,closestEnemy,gs,ru);
        }
        else
        {
            return Attack(u,null,gs,ru);
        }*/
    }

    @Override
    protected UnitAction scoutBehavior(Unit scout, Player p, GameState gs, ResourceUsage ru) {
        Unit closestEnemy = null;
        Unit base = null;
        int closestEnemyDistance = 0;
        int closestBaseDistance = 0;

        PhysicalGameState pgs = gs.getPhysicalGameState();

        for(Unit u2:pgs.getUnits()) {
            if (u2.getPlayer()>=0 && u2.getPlayer()!=p.getID()) {
                int d = evaluateAttackTarget(scout, u2, pgs);
                if (closestEnemy==null || d<closestEnemyDistance) {
                    closestEnemy = u2;
                    closestEnemyDistance = d;
                }
            }
            if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                int d = Math.abs(u2.getX() - scout.getX()) + Math.abs(u2.getY() - scout.getY());
                if (base==null || d<closestBaseDistance) {
                    base = u2;
                    closestBaseDistance = d;
                }
            }
        }

        if(closestEnemy!=null){
            //System.out.println("Sees enemy at: " + closestEnemy.getX() + " " + closestEnemy.getY());
            if(pf.findPathToAdjacentPosition(scout, closestEnemy.getPosition(pgs), gs, ru)!=null
                    || manhattanDistance(scout.getX(), scout.getY(), closestEnemy.getX(), closestEnemy.getY()) == 1){
                if(isChokepoint(scout.getX(), scout.getY(), pgs)){
                    if(isPlayerPredominant(scout, 5, 5, gs))
                        return Attack(scout, closestEnemy, gs, ru);
                    else
                        return Idle(scout, gs, ru);
                }
                else
                    return Attack(scout, closestEnemy, gs, ru);
            }
            else{
                //System.out.println("Resources: " + scout.getResources());
                if(scout.getResources() == 0)
                    return Scout(scout,closestEnemy,gs,ru);
                else if (base!=null){
                    //System.out.println("base at " + base.getX() + " " + base.getY());
                    if(pf.findPathToAdjacentPosition(scout, base.getPosition(pgs), gs, ru)!=null
                            || manhattanDistance(scout.getX(), scout.getY(), base.getX(), base.getY()) == 1){
                        //System.out.println("Go back");
                        return Harvest(scout, null, base, gs, ru);
                    }
                    else
                        return Scout(scout,closestEnemy,gs,ru);
                }

            }
        }

        return Scout(scout,null,gs,ru);
    }

    @Override
    protected UnitAction builderBehavior(Unit worker, Player p, GameState gs, ResourceUsage ru) {
        PhysicalGameState pgs = gs.getPhysicalGameState();

        if(playerUnits[4] == 0 && p.getResources() >= baseType.cost){
            int highscore = 0, pos = 0;

            List<Integer> positions = GetFreePositionsAround(worker, pgs);

            if(positions.isEmpty())
                return workerBehavior(worker, p, gs, ru);

            for(int i : positions){
                int score = evaluateBaseBuild(worker, i, p.getID(), pgs);
                if(score > highscore){
                    highscore = score;
                    pos = i;
                }
            }

            int x = pos % pgs.getWidth();
            int y = pos / pgs.getWidth();

            return Build(worker, baseType, x, y, gs, ru);
        }

        if(playerUnits[5] < totBarracks && p.getResources() >= barracksType.cost){
            int highscore = 0, pos = 0;
            List<Integer> positions;

            Unit base = GetClosestBase(worker, pgs);

            if(base == null)
                positions = GetFreePositionsAround(worker, pgs);
            else
                positions = GetFreePositionsAround(base, pgs);

            if(positions.isEmpty())
                return workerBehavior(worker, p, gs, ru);

            for(int i : positions){
                int score = evaluateBarrackBuild(i, p.getID(), pgs);
                if(score > highscore || highscore == 0){
                    highscore = score;
                    pos = i;
                }
            }

            int x = pos % pgs.getWidth();
            int y = pos / pgs.getWidth();

            return Build(worker, barracksType, x, y, gs, ru);
        }

        return workerBehavior(worker, p, gs, ru);
    }

    @Override
    protected List<Pair<Unit, UnitAction>> workersBehavior(List<Unit> workers, Player p, GameState gs, ResourceUsage ru) {
        int nbases = 0;
        int nbarracks = 0;
        int resourcesUsed = 0;
        Unit hw;
        UnitAction unitAction;
        List<Unit> harvestWorkers = new LinkedList<>();
        List<Unit> freeWorkers = new LinkedList<>(workers);
        PhysicalGameState pgs = gs.getPhysicalGameState();
        List<Pair<Unit, UnitAction>> list = new ArrayList<>();

        if (workers.isEmpty()) return list;

        nbases = playerUnits[4];
        nbarracks = playerUnits[5];

        List<Integer> reservedPositions = new LinkedList<>();
        if (nbases==0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources()>=baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                //buildIfNotAlreadyBuilding(u,baseType,u.getX(),u.getY(),reservedPositions,p,pgs);
                if(gs.getActionAssignment(u) == null){
                    resourcesUsed+=baseType.cost;
                    unitAction = builderBehavior(u, p, gs, ru);

                    if(conflictingMove(u, unitAction, list, pgs))
                        unitAction = Idle(u, gs, ru);

                    list.add(new Pair<>(u, unitAction));
                }
            }
        }

        if (freeWorkers.size()>0){
            for(int i = 1; i <=nHarvest; i++){
                if(!freeWorkers.isEmpty()){
                    hw = freeWorkers.remove(0);
                    harvestWorkers.add(hw);
                }
            }
        }

        if (harvestWorkers.size() >= 1 && nbarracks < totBarracks && p.getResources() > barracksType.cost){
            Unit u;

            if(harvestWorkers.size()==1 && freeWorkers.size() > 0)
                u = freeWorkers.remove(0);
            else
                u = harvestWorkers.remove(0);

            //buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
            if(gs.getActionAssignment(u) == null){
                resourcesUsed += barracksType.cost;
                unitAction = builderBehavior(u, p, gs, ru);

                if(conflictingMove(u, unitAction, list, pgs))
                    unitAction = Idle(u, gs, ru);

                list.add(new Pair<>(u, unitAction));
            }
        }

        // harvest with the harvest worker:
        for (Unit harvestWorker : harvestWorkers) {
            if(gs.getActionAssignment(harvestWorker) != null)
                continue;

            Unit closestBase = null;
            Unit closestResource = null;
            Unit closestEnemy = null;

            int closestResourceDistance = 0;
            int closestBaseDistance = 0;
            int closestEnemyDistance = 0;

            for(Unit u2:pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource==null || d<closestResourceDistance) {
                        closestResource = u2;
                        closestResourceDistance = d;
                    }
                }

                if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestBase==null || d<closestBaseDistance) {
                        closestBase = u2;
                        closestBaseDistance = d;
                    }
                }

                if(u2.getPlayer() >= 0 && u2.getPlayer()!=harvestWorker.getPlayer()){
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestEnemy==null || d<closestEnemyDistance) {
                        closestEnemy = u2;
                        closestEnemyDistance = d;
                    }
                }
            }

            //if you are close to a base and an enemy is close to that, attack him
            if(closestEnemy != null && closestEnemyDistance <= 2 && closestBaseDistance <= 2)
                list.add(new Pair<>(harvestWorker, Attack(harvestWorker, closestEnemy, gs, ru)));

            else if (closestResource!=null && closestBase!=null) {

                if(closestEnemy!=null){
                    if(Between(closestEnemy, harvestWorker, closestResource) && harvestWorker.getResources()<1){
                        //this could be changed directly to Attack
                        unitAction = scoutBehavior(harvestWorker, p, gs, ru);
                    }
                    else
                        unitAction = Harvest(harvestWorker, closestResource, closestBase, gs, ru);
                }
                else
                    unitAction = Harvest(harvestWorker, closestResource, closestBase, gs, ru);

                if(conflictingMove(harvestWorker, unitAction, list, pgs))
                    unitAction = Idle(harvestWorker, gs, ru);

                list.add(new Pair<>(harvestWorker, unitAction));
            }
            else if(closestBase!=null && harvestWorker.getResources()>0){
                unitAction = Harvest(harvestWorker, null, closestBase, gs, ru);

                if(conflictingMove(harvestWorker, unitAction, list, pgs))
                    unitAction = Idle(harvestWorker, gs, ru);

                list.add(new Pair<>(harvestWorker, unitAction));
            }
            else
                freeWorkers.add(harvestWorker);
        }

        for(Unit u:freeWorkers){
            if(gs.getActionAssignment(u) == null){
                unitAction = scoutBehavior(u, p, gs, ru);

                if(conflictingMove(u, unitAction, list, pgs))
                    unitAction = Idle(u, gs, ru);

                list.add(new Pair<>(u, unitAction));
            }
        }

        return list;
    }
}