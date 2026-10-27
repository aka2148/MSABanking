#!/usr/bin/env node
// Disposable-gateway smoke runner. Configure BASE_URL and credentials in the
// environment; it never writes secrets or starts/stops a shared compose stack.
const base=(process.env.BASE_URL||'http://localhost').replace(/\/$/,'');
const steps=[]; const pass=(name)=>{steps.push([name,true]);console.log(`PASS ${name}`)};
const fail=(name,error)=>{steps.push([name,false]);console.log(`FAIL ${name}: ${error}`)};
async function request(name,path,options={}){try{const response=await fetch(`${base}${path}`,{...options,headers:{'Content-Type':'application/json',...(options.headers||{})}});if(!response.ok)throw new Error(`${response.status} ${await response.text()}`);return response;}catch(error){fail(name,error.message);return null;}}
const username=process.env.E2E_USERNAME,password=process.env.E2E_PASSWORD;
if(!username||!password){fail('configuration','Set E2E_USERNAME and E2E_PASSWORD for an isolated stack');process.exit(1);}
const login=await request('login','/api/v1/auth/login',{method:'POST',body:JSON.stringify({username,password})});
if(login){const body=await login.json();const token=body.token;pass('authenticated login');
  const auth={Authorization:`Bearer ${token}`};
  const products=await request('eligible FD products','/api/v1/fd-products/eligible',{headers:auth});if(products)pass('eligible FD products readable');
  const negative=await fetch(`${base}/api/v1/fixed-deposits/estimate`,{method:'POST',headers:{...auth,'Content-Type':'application/json'},body:JSON.stringify({productId:0,currencyCode:'INR',principal:0,tenureMonths:0})});
  if(negative.status>=400&&negative.status<500)pass('estimate validation returns a client error');else fail('estimate validation',`unexpected status ${negative.status}`);
}
if(steps.some(([,ok])=>!ok))process.exitCode=1;
