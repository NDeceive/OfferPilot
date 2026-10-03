import request from '../utils/request'
export const teaching = {
 classes:()=>request.get('/teaching/classes'),createClass:data=>request.post('/teaching/classes',data),editClass:(id,data)=>request.put(`/teaching/classes/${id}`,data),
 join:inviteCode=>request.post('/teaching/classes/join',{inviteCode}),invite:id=>request.post(`/teaching/classes/${id}/invite`),archive:id=>request.post(`/teaching/classes/${id}/archive`),
 members:id=>request.get(`/teaching/classes/${id}/members`),membership:(id,member,state)=>request.put(`/teaching/classes/${id}/members/${member}/${state}`),
 people:()=>request.get('/teaching/students'),summary:()=>request.get('/teaching/summary'),tasks:params=>request.get('/teaching/tasks',{params}),task:id=>request.get(`/teaching/tasks/${id}`),
 createTask:data=>request.post('/teaching/tasks',data),publish:id=>request.post(`/teaching/tasks/${id}/publish`),extend:(id,deadline)=>request.put(`/teaching/tasks/${id}/deadline`,{deadline}),end:id=>request.post(`/teaching/tasks/${id}/end`),remind:id=>request.post(`/teaching/tasks/${id}/remind`),
 editTask:(id,data)=>request.put(`/teaching/tasks/${id}`,data),discard:id=>request.delete(`/teaching/tasks/${id}`),
 supplement:(id,data)=>request.post(`/teaching/tasks/${id}/supplement`,data),override:(id,assignmentId,data)=>request.put(`/teaching/tasks/${id}/assignments/${assignmentId}`,data),
 mine:()=>request.get('/teaching/assignments'),assignment:id=>request.get(`/teaching/assignments/${id}`),retry:id=>request.post(`/teaching/sessions/${id}/retry`),
 report:id=>request.get(`/teaching/reports/${id}`),reviews:id=>request.get(`/teaching/reports/${id}/reviews`),review:(id,text)=>request.post(`/teaching/reports/${id}/reviews`,{text}),
 messages:()=>request.get('/teaching/messages'),read:id=>request.put(`/teaching/messages/${id}/read`),
}
