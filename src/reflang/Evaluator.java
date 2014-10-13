package reflang;
import static reflang.AST.*;
import static reflang.Value.*;
import static reflang.Store.*;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

import reflang.Env.*;

public class Evaluator implements Visitor<Value> {
	
	Printer.Formatter ts = new Printer.Formatter();

	Env initEnv = initialEnv(); //New for definelang
	
    Store store = null; //New for reflang

    Value valueOf(Program p) {
    	store = new Store32Bit();
		return (Value) p.accept(this, initEnv);
	}
	
	@Override
	public Value visit(AddExp e, Env env) {
		List<Exp> operands = e.all();
		int result = 0;
		for(Exp exp: operands) {
			NumVal intermediate = (NumVal) exp.accept(this, env); // Dynamic type-checking
			result += intermediate.v(); //Semantics of AddExp in terms of the target language.
		}
		return new NumVal(result);
	}
	
	@Override
	public Value visit(Unit e, Env env) {
		return new UnitVal();
	}

	@Override
	public Value visit(Const e, Env env) {
		return new NumVal(e.v());
	}

	@Override
	public Value visit(StrConst e, Env env) {
		return new StringVal(e.v());
	}

	@Override
	public Value visit(BoolConst e, Env env) {
		return new BoolVal(e.v());
	}

	@Override
	public Value visit(DivExp e, Env env) {
		List<Exp> operands = e.all();
		NumVal lVal = (NumVal) operands.get(0).accept(this, env);
		double result = lVal.v(); 
		for(int i=1; i<operands.size(); i++) {
			NumVal rVal = (NumVal) operands.get(i).accept(this, env);
			result = result / rVal.v();
		}
		return new NumVal(result);
	}

	@Override
	public Value visit(ErrorExp e, Env env) {
		return new Value.DynamicError("Encountered an error expression");
	}

	@Override
	public Value visit(MultExp e, Env env) {
		List<Exp> operands = e.all();
		double result = 1;
		for(Exp exp: operands) {
			NumVal intermediate = (NumVal) exp.accept(this, env); // Dynamic type-checking
			result *= intermediate.v(); //Semantics of MultExp.
		}
		return new NumVal(result);
	}

	@Override
	public Value visit(Program p, Env env) {
		for(DefineDecl d: p.decls())
			d.accept(this, initEnv);
		return (Value) p.e().accept(this, initEnv);
	}

	@Override
	public Value visit(SubExp e, Env env) {
		List<Exp> operands = e.all();
		NumVal lVal = (NumVal) operands.get(0).accept(this, env);
		double result = lVal.v();
		for(int i=1; i<operands.size(); i++) {
			NumVal rVal = (NumVal) operands.get(i).accept(this, env);
			result = result - rVal.v();
		}
		return new NumVal(result);
	}

	@Override
	public Value visit(VarExp e, Env env) {
		// Previously, all variables had value 42. New semantics.
		return env.get(e.name());
	}	

	@Override
	public Value visit(LetExp e, Env env) { // New for varlang.
		List<String> names = e.names();
		List<Exp> value_exps = e.value_exps();
		List<Value> values = new ArrayList<Value>(value_exps.size());
		
		for(Exp exp : value_exps) 
			values.add((Value)exp.accept(this, env));
		
		Env new_env = env;
		for (int index = 0; index < names.size(); index++)
			new_env = new ExtendEnv(new_env, names.get(index), values.get(index));

		return (Value) e.body().accept(this, new_env);		
	}	
	
	@Override
	public Value visit(DefineDecl e, Env env) { // New for definelang.
		String name = e.name();
		Exp value_exp = e.value_exp();
		Value value = (Value) value_exp.accept(this, env);
		initEnv = new ExtendEnv(initEnv, name, value);
		return new Value.UnitVal();		
	}	

	@Override
	public Value visit(LambdaExp e, Env env) { // New for funclang.
		return new Value.FunVal(env, e.formals(), e.body());
	}
	
	@Override
	public Value visit(CallExp e, Env env) { // New for funclang.
		Object result = e.operator().accept(this, env);
		if(!(result instanceof Value.FunVal))
			return new Value.DynamicError("Operator not a function in call " +  ts.visit(e, env));
		Value.FunVal operator =  (Value.FunVal) result; //Dynamic checking
		List<Exp> operands = e.operands();

		// Call-by-value semantics
		List<Value> actuals = new ArrayList<Value>(operands.size());
		for(Exp exp : operands) 
			actuals.add((Value)exp.accept(this, env));
		
		List<String> formals = operator.formals();
		if (formals.size()!=actuals.size())
			return new Value.DynamicError("Argument mismatch in call " + ts.visit(e, env));

		Env closure_env = operator.env();
		Env fun_env = appendEnv(closure_env, initEnv);
		for (int index = 0; index < formals.size(); index++)
			fun_env = new ExtendEnv(fun_env, formals.get(index), actuals.get(index));
		
		return (Value) operator.body().accept(this, fun_env);
	}
	
	/* Helper for CallExp */
	/***
	 * Create an env that has bindings from fst appended to bindings from snd.
	 * The order of bindings is bindings from fst followed by that from snd.
	 * @param fst
	 * @param snd
	 * @return
	 */
	private Env appendEnv(Env fst, Env snd){
		if(fst.isEmpty()) return snd;
		if(fst instanceof ExtendEnv) {
			ExtendEnv f = (ExtendEnv) fst;
			return new ExtendEnv(appendEnv(f.saved_env(),snd), f.var(), f.val());
		}
		ExtendEnvRec f = (ExtendEnvRec) fst;
		return new ExtendEnvRec(appendEnv(f.saved_env(),snd), f.names(), f.vals());
	}
	/* End: helper for CallExp */
	
	@Override
	public Value visit(IfExp e, Env env) { // New for funclang.
		Object result = e.conditional().accept(this, env);
		if(!(result instanceof Value.BoolVal))
			return new Value.DynamicError("Condition not a boolean in expression " +  ts.visit(e, env));
		Value.BoolVal condition =  (Value.BoolVal) result; //Dynamic checking
		
		if(condition.v())
			return (Value) e.then_exp().accept(this, env);
		else return (Value) e.else_exp().accept(this, env);
	}

	@Override
	public Value visit(LessExp e, Env env) { // New for funclang.
		Value.NumVal first = (Value.NumVal) e.first_exp().accept(this, env);
		Value.NumVal second = (Value.NumVal) e.second_exp().accept(this, env);
		return new Value.BoolVal(first.v() < second.v());
	}
	
	@Override
	public Value visit(EqualExp e, Env env) { // New for funclang.
		Value.NumVal first = (Value.NumVal) e.first_exp().accept(this, env);
		Value.NumVal second = (Value.NumVal) e.second_exp().accept(this, env);
		return new Value.BoolVal(first.v() == second.v());
	}

	@Override
	public Value visit(GreaterExp e, Env env) { // New for funclang.
		Value.NumVal first = (Value.NumVal) e.first_exp().accept(this, env);
		Value.NumVal second = (Value.NumVal) e.second_exp().accept(this, env);
		return new Value.BoolVal(first.v() > second.v());
	}
	
	@Override
	public Value visit(CarExp e, Env env) { 
		Value.PairVal pair = (Value.PairVal) e.arg().accept(this, env);
		return pair.fst();
	}
	
	@Override
	public Value visit(CdrExp e, Env env) { 
		Value.PairVal pair = (Value.PairVal) e.arg().accept(this, env);
		Value result = pair.snd();
		//Special case for list: cdr of a list is a list also.
		if(pair instanceof Value.ExtendList) {
			if(result instanceof Value.EmptyList) return result;
			return new ExtendList(((Value.PairVal)result).fst(),((Value.PairVal)result).snd());
		}
		return pair.snd();
	}
	
	@Override
	public Value visit(ConsExp e, Env env) { 
		Value first = (Value) e.fst().accept(this, env);
		Value second = (Value) e.snd().accept(this, env);
		//Special case for list: cdr of a list is a list also.
		if(second instanceof Value.EmptyList) 
			return new Value.ExtendList(first, second);
		else if(second instanceof Value.ExtendList) {
			ExtendList rest = (Value.ExtendList) second;
			PairVal newSecond = new PairVal(rest.fst(), rest.snd());
			return new ExtendList(first, newSecond);
		}
		return new Value.PairVal(first, second);
	}

	@Override
	public Value visit(ListExp e, Env env) { // New for funclang.
		List<Exp> elemExps = e.elems();
		int length = elemExps.size();
		if(length == 0)
			return new Value.EmptyList();
		
		List<Value> elems = new ArrayList<Value>(length);
		for(Exp exp : elemExps) 
			elems.add((Value) exp.accept(this, env));
		
		Value.PairVal list = null;
		for(int i=length-1; i>0; i--) {
			if(list == null)
				list = new PairVal(elems.get(i), new Value.EmptyList());
			else list = new PairVal(elems.get(i),list);
		}
		if(list == null) list = new ExtendList(elems.get(0), new Value.EmptyList());
		else list = new ExtendList(elems.get(0),list);
		return list;
	}
	
	@Override
	public Value visit(NullExp e, Env env) {
		Value val = (Value) e.arg().accept(this, env);
		return new BoolVal(val instanceof Value.EmptyList);
	}

	public Value visit(EvalExp e, Env env) {
		StringVal programText = (StringVal) e.code().accept(this, env);
		Program p = _reader.parse(programText.v());
		return (Value) p.accept(this, env);
	}

	public Value visit(ReadExp e, Env env) {
		StringVal fileName = (StringVal) e.file().accept(this, env);
		String text = Reader.readFile("" + System.getProperty("user.dir") + File.separator + fileName.v());
		return new StringVal(text);
	}
	
	@Override
	public Value visit(LetrecExp e, Env env) { // New for reclang.
		List<String> names = e.names();
		List<Exp> fun_exps = e.fun_exps();
		List<Value.FunVal> funs = new ArrayList<Value.FunVal>(fun_exps.size());
		
		for(Exp exp : fun_exps) 
			funs.add((Value.FunVal)exp.accept(this, env));

		Env new_env = new ExtendEnvRec(env, names, funs);
		return (Value) e.body().accept(this, new_env);		
	}	
    
        @Override
        public Value visit(RefExp e, Env env) { // New for reflang.
                Exp value_exp = e.value_exp();
                Value value = (Value) value_exp.accept(this, env);
                Value.Loc new_loc = store.ref(value);
                return new_loc;
        }
    
        @Override
        public Value visit(DerefExp e, Env env) { // New for reflang.
                Exp loc_exp = e.loc_exp();
                Value.Loc loc = (Value.Loc) loc_exp.accept(this, env);
                return store.deref(loc);
        }
    
        @Override
        public Value visit(AssignExp e, Env env) { // New for reflang.
                Exp rhs = e.rhs_exp();
                Exp lhs = e.lhs_exp();
                //Note the order of evaluation below.
                Value rhs_val = (Value) rhs.accept(this, env);
                Value.Loc loc = (Value.Loc) lhs.accept(this, env);
                Value assign_val = store.setref(loc, rhs_val);
                return assign_val;
        }
        
        @Override
        public Value visit(FreeExp e, Env env) { // New for reflang.
                Exp value_exp = e.value_exp();
                Value.Loc loc = (Value.Loc) value_exp.accept(this, env);
                store.free(loc);
                return new Value.UnitVal();
        }
    
        private Env initialEnv() {
		Env initEnv = new EmptyEnv();
		
		/* Procedure: (read <filename>). Following is same as (define read (lambda (file) (read file))) */
		List<String> formals = new ArrayList<>();
		formals.add("file");
		Exp body = new AST.ReadExp(new VarExp("file"));
		Value.FunVal readFun = new Value.FunVal(initEnv, formals, body);
		initEnv = new Env.ExtendEnv(initEnv, "read", readFun);

		/* Procedure: (require <filename>). Following is same as (define require (lambda (file) (eval (read file)))) */
		formals = new ArrayList<>();
		formals.add("file");
		body = new EvalExp(new AST.ReadExp(new VarExp("file")));
		Value.FunVal requireFun = new Value.FunVal(initEnv, formals, body);
		initEnv = new Env.ExtendEnv(initEnv, "require", requireFun);
		
		/* Add new built-in procedures here */ 
		
		return initEnv;
	}
	
	Reader _reader; 
	public Evaluator(Reader reader) {
		_reader = reader;
	}
}
