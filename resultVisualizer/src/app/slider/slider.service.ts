import { Injectable, EventEmitter } from '@angular/core';
import { DiagramService } from '../diagram/service/diagram.service';
import { Scale } from '../model/defaultScale';
import { Observable } from 'rxjs/Observable';


@Injectable()
export class SliderService {

  private _scales : Array<Scale>
  private _scaleChangeEvent : EventEmitter<Scale>
  constructor(private _diagramService: DiagramService) {
    this._scaleChangeEvent = new EventEmitter<Scale>();
    this._scales = this._diagramService.getScale();
    if(this._scales === null || this._scales === undefined){
      this._diagramService.InitEvent.subscribe((event) =>{
        switch(event){
          case "Config":
          this._scales = this._diagramService.getScale();
            break;
        }
      })
   };
  }
   get Scales(){
    return this._scales;
   }

   get ScaleChangeEvent(){
     return this._scaleChangeEvent;
   }

   public changeScale(scale :Scale){
     this._scaleChangeEvent.emit(scale);
   }
  }

  
