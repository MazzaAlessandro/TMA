package ai.tma.strategies;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.WorkerRushPlusPlus;
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class AwareWorkerRush extends AbstractAwareAI {

    Random r = new Random();
    boolean resourse = true;

    // Strategy implemented by this class:
    // If we have more than 1 "Worker": send the extra workers to attack to the nearest enemy unit
    //attack base and barracks first
    // If we have a base: train workers non-stop
    // If we have a worker: do this if needed: build base, harvest resources
    public AwareWorkerRush(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }


    public AwareWorkerRush(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public AI clone() {
        return new WorkerRushPlusPlus(utt, pf);
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

        return translateActions(player,gs);
    }*/


    public void baseBehavior(Unit u,Player p, GameState pgs) {
        if (p.getResources()>=workerType.cost) train(u, workerType);
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
            /*if(pf.findPathToPositionInRange(u,
                    closestEnemy.getX()+closestEnemy.getY()*gs.getPhysicalGameState().getWidth(), u.getAttackRange(), gs, gs.getResourceUsage()) != null)
                attack(u,closestEnemy);
            else{
                moveTowards(u, closestEnemy, gs);
            }*/
        }
        else
        {
            attack(u, null);
        }

    }

    public void workersBehavior(List<Unit> workers,Player p, GameState gs) {
        int nbases = 0;
        int resourcesUsed = 0;
        Unit hw;
        List<Unit> harvestWorkers = new LinkedList<>();
        List<Unit> freeWorkers = new LinkedList<>(workers);
        PhysicalGameState pgs = gs.getPhysicalGameState();

        if (workers.isEmpty()) return;

        for(Unit u2:pgs.getUnits()) {
            if (u2.getType() == baseType &&
                    u2.getPlayer() == p.getID()) nbases++;
        }

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

        // harvest with the harvest worker:
        for (Unit harvestWorker : harvestWorkers) {
            /*Target t = GetPriorityTarget(harvestWorker);
            PriorityMovesType priorityMove = null;
            if (t != null)
                priorityMove = t.move;
            if(priorityMove == null || priorityMove != PriorityMovesType.ENGAGE){
                //here was the code
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


    @Override
    public List<ParameterSpecification> getParameters()
    {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }

    @Override
    protected void barracksBehavior(Unit u, Player p, GameState gs) {
        if (p.getResources()>=5) train(u, lightType);
    }
}

