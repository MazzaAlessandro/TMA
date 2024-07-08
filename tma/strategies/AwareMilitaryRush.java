package ai.tma.strategies;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.EconomyMilitaryRush;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.*;

public class AwareMilitaryRush extends AbstractAwareAI {

    Random r = new Random();
    int nWorkerBase = 4 * 2;

    // If we have any unit for attack: send it to attack to the nearest enemy unit
    // If we have a base: train worker until we have 8 workers per base. The 8ª unit send to build a new base.
    // If we have a barracks: train light, Ranged and Heavy in order
    // If we have a worker: go to resources closest, build barracks, build new base closest harvest resources
    public AwareMilitaryRush(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }

    public AwareMilitaryRush(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    /*public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
//        System.out.println("LightRushAI for player " + player + " (cycle " + gs.getTime() + ")");

        List<Unit> workers = new LinkedList<>();

        for(Unit u:pgs.getUnits()) {
            if (HasPriorityTarget(u))
                PriorityMove(u, GetPriorityTarget(u));
            else{
                Target t;
                // behavior of bases:
                if (u.getType()==baseType &&
                        u.getPlayer() == player &&
                        gs.getActionAssignment(u)==null) {
                    baseBehavior(u,p,pgs);
                }

                // behavior of melee units:
                if (u.getType().canAttack && !u.getType().canHarvest &&
                        u.getPlayer() == player &&
                        gs.getActionAssignment(u)==null) {
                    t = GetBestTarget(gs, u, distance);
                    if (t != null && t.evaluateTrain >= strategyPriority){
                        AddPriorityTarget(u, t);
                        PriorityMove(u, t);
                    }
                    else
                        meleeUnitBehavior(u,p,pgs);
                }

                // behavior of barracks:
                if (u.getType() == barracksType
                        && u.getPlayer() == player
                        && gs.getActionAssignment(u) == null) {
                    barracksBehavior(u, p, pgs);
                }


                // behavior of workers:
                if (u.getType().canHarvest &&
                        u.getPlayer() == player) {
                    t = GetBestTarget(gs, u, distance);
                    if (t != null && t.evaluateTrain >= strategyPriority){
                        AddPriorityTarget(u, t);
                        PriorityMove(u, t);
                    }
                    else
                        workers.add(u);
                }
            }

        }

        workersBehavior(workers,p,pgs);

        /*
        // behavior of melee units:
        for(Unit u:pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest &&
                    u.getPlayer() == player &&
                    gs.getActionAssignment(u)==null) {
                meleeUnitBehavior(u,p,gs);
            }
        }

        // behavior of workers:
        List<Unit> workers = new LinkedList<>();
        for(Unit u:pgs.getUnits()) {
            if (u.getType().canHarvest &&
                    u.getPlayer() == player) {
                workers.add(u);
            }
        }
        workersBehavior(workers,p,gs);



        return translateActions(player,gs);
    }*/

    @Override
    public AI clone() {
        return new AwareMilitaryRush(utt, pf);
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }

    public void baseBehavior(Unit u, Player p, GameState gs) {
        if ((p.getResources()>=workerType.cost && playerUnits[0] < nHarvest) ||
                (p.getResources() > 2 * playerUnits[5] && p.getResources() >= 5 && playerUnits[5] >= totBarracks)) train(u, workerType);
    }

    public void barracksBehavior(Unit u, Player p, GameState gs) {
        int unit = 0, value = 0;
        UnitType type;
        for(int i = 0; i < unitProduction.length; i++){
            if(unitProduction[i] > value){
                value = unitProduction[i];
                unit = i;
            }
        }

        switch (unit){
            case 0:
                type = lightType;
                break;
            case 1:
                type = heavyType;
                break;
            case 2:
                type = rangedType;
                break;
            default:
                type = lightType;
                break;
        }
        if(p.getResources() >= type.cost)
            train(u, type);
    }

    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        Unit closestEnemy = null;
        Unit closestMeleeEnemy = null;
        int closestDistance = 0;
        int enemyDistance = 0;
        int mybase = 0;

        PhysicalGameState pgs = gs.getPhysicalGameState();

        /*Target t = GetPriorityTarget(u);


        if(t != null && t.evaluateTrain >= strategyPriority){
            PriorityMove(u, t, gs);
        }*/

        for(Unit u2:pgs.getUnits()) {
            if (u2.getPlayer()>=0 && u2.getPlayer()!=p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy==null || d<closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy!=null) {
            engage(u,closestEnemy);
        }
        else
        {
            attack(u, null);
        }
    }

    public void workersBehavior(List<Unit> workers, Player p, GameState gs) {
        /*int nbases = 0;
        int nbarracks = 0;
        int resourcesUsed = 0;
        int nArmyUnits = 0;

        List<Unit> freeWorkers = new ArrayList<>(workers);
        PhysicalGameState pgs = gs.getPhysicalGameState();

        if (workers.isEmpty()) {
            return;
        }

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) {
                nbarracks++;
            }
            if ( (u2.getType() == lightType || u2.getType() == rangedType || u2.getType() == heavyType)
                    && u2.getPlayer() == p.getID()) {
                nArmyUnits++;
            }
        }

        List<Integer> reservedPositions = new ArrayList<>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() >= baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
                resourcesUsed += baseType.cost;
            }
        }

        if (nbarracks == 0 && !freeWorkers.isEmpty()) {
            // build a barracks:
            if (p.getResources() >= barracksType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
                resourcesUsed += barracksType.cost;
            }
        }else if (nbarracks > 0 && !freeWorkers.isEmpty() && nArmyUnits > 2){
            // build a new barracks:
            if (p.getResources() >= barracksType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
                resourcesUsed += barracksType.cost;
            }
        }

        if (nbarracks != 0) {
            List<Unit> otherResources = new ArrayList<>(otherResourcePoint(p, pgs));
            if (!otherResources.isEmpty()) {
                if (!freeWorkers.isEmpty()) {
                    //envio para construção
                    if (p.getResources() >= baseType.cost + resourcesUsed) {
                        Unit u = freeWorkers.remove(0);
                        buildIfNotAlreadyBuilding(u, baseType, otherResources.get(0).getX()+1, otherResources.get(0).getY()+1, reservedPositions, p, pgs);
                        resourcesUsed += baseType.cost;
                    }
                }
            }
        }
        // harvest with all the free workers:
        harvestWorkers(freeWorkers, p, gs);*/

        int nbases = 0;
        int nbarracks = 0;
        int resourcesUsed = 0;
        Unit hw;
        List<Unit> harvestWorkers = new LinkedList<>();
        List<Unit> freeWorkers = new LinkedList<>(workers);
        PhysicalGameState pgs = gs.getPhysicalGameState();

        if (workers.isEmpty()) return;

        /*for(Unit u2:pgs.getUnits()) {
            if (u2.getType() == baseType &&
                    u2.getPlayer() == p.getID()) nbases++;
        }*/
        nbases = playerUnits[4];
        nbarracks = playerUnits[5];

        List<Integer> reservedPositions = new LinkedList<>();
        if (nbases==0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources()>=baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,baseType,u.getX(),u.getY(),reservedPositions,p,pgs);
                resourcesUsed+=baseType.cost;
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

        if (harvestWorkers.size() > 1 && nbarracks < totBarracks && p.getResources() >= barracksType.cost){
            Unit u = harvestWorkers.remove(0);
            buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
            resourcesUsed += barracksType.cost;
        }

        // harvest with the harvest worker:
        for (Unit harvestWorker : harvestWorkers) {
            /*Target t = GetPriorityTarget(harvestWorker);
            PriorityMovesType priorityMove = null;
            if (t != null)
                priorityMove = t.move;
            if(priorityMove == null || priorityMove != PriorityMovesType.ENGAGE){

            }
            else{
                PriorityMove(harvestWorker, t, gs);
            }*/
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for(Unit u2:pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource==null || d<closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }

            closestDistance = 0;
            for(Unit u2:pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestBase==null || d<closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource!=null && closestBase!=null) {
                AbstractAction aa = getAbstractAction(harvestWorker);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest)aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase()!=closestBase) {
                        harvest(harvestWorker, closestResource, closestBase);
                    } else {
                    }
                } else {
                    harvest(harvestWorker, closestResource, closestBase);
                }
            }
            else
                freeWorkers.add(harvestWorker);
        }

        for(Unit u:freeWorkers) meleeUnitBehavior(u, p, gs);

    }

    protected List<Unit> otherResourcePoint(Player p, PhysicalGameState pgs) {

        List<Unit> bases = getMyBases(p, pgs);
        Set<Unit> myResources = new HashSet<>();
        Set<Unit> otherResources = new HashSet<>();

        for (Unit base : bases) {
            List<Unit> closestUnits = new ArrayList<>(pgs.getUnitsAround(base.getX(), base.getY(), 10));
            for (Unit closestUnit : closestUnits) {
                if (closestUnit.getType().isResource) {
                    myResources.add(closestUnit);
                }
            }
        }

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType().isResource) {
                if (!myResources.contains(u2)) {
                    otherResources.add(u2);
                }
            }
        }

        return new ArrayList<>(otherResources);
    }

    protected List<Unit> getMyBases(Player p, PhysicalGameState pgs) {

        List<Unit> bases = new ArrayList<>();
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                bases.add(u2);
            }
        }
        return bases;
    }

    protected void harvestWorkers(List<Unit> freeWorkers, Player p, GameState gs) {
        for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            PhysicalGameState pgs = gs.getPhysicalGameState();
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource != null && closestBase != null) {
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest) aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) {
                        harvest(u, closestResource, closestBase);
                    }
                } else {
                    harvest(u, closestResource, closestBase);
                }
            }
        }
    }
}

